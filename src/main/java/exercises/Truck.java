package exercises;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModels;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import exercises.PickupAndDelivery.Messages;

public class Truck extends Vehicle implements CommUser {

	private static final double SPEED = 1000d;
	private Optional<RoadModel> roadModel;
	private Optional<PDPModel> pdpModel;
	private Optional<Parcel> curr;
	
	Optional<CommDevice> device;	
	private static final double RANGE = 1000;
	private final double reliability;
	private final RandomGenerator rng;
	long lastReceiveTime = 0;
	
	boolean available;
	
	Truck(Point startPosition, double capacity, RandomGenerator r) {
		setStartPosition(startPosition);
		setCapacity(capacity);
		roadModel = Optional.absent();
		pdpModel = Optional.absent();
		curr = Optional.absent();
		
		rng = r;
		reliability = 1; //rng.nextDouble();
		
		device = Optional.absent();
		
		available = true;
	}
	

	@Override
	public double getSpeed() {
		return SPEED;
	}

	@Override
	protected void tickImpl(TimeLapse time) {
		final RoadModel rm = roadModel.get();
		final PDPModel pm = pdpModel.get();

		if (!time.hasTimeLeft()) {
			return;
		}
		
		
		if (device.get().getUnreadCount() > 0 && available) {
			ImmutableList<Message> messages = device.get().getUnreadMessages();
			
			for (int i = 0; i < messages.size(); i++) {
				if (messages.get(i).getContents().equals(Messages.WHO_CAN_PICK_ME_UP)) {
					device.get().send(Messages.I_CAN, messages.get(i).getSender());
				}
				else if (messages.get(i).getContents().equals(Messages.PICK_ME_UP)) {
					curr = Optional.fromNullable(RoadModels.findClosestObject(
							messages.get(i).getSender().getPosition().get(), rm, Parcel.class));
					available = false;
					break;
				}
			}
		}
		
		if (!curr.isPresent()) {
			available = true;
		}
		if (curr.isPresent()) {
			final boolean inCargo = pm.containerContains(this, curr.get());
			// sanity check: if it is not in our cargo AND it is also not on the
			// RoadModel, we cannot go to curr anymore.
			if (!inCargo && !rm.containsObject(curr.get())) {
				curr = Optional.absent();
				available = true;
			} else if (inCargo) {
				// if it is in cargo, go to its destination
				rm.moveTo(this, curr.get().getDestination(), time);
				if (rm.getPosition(this).equals(curr.get().getDestination())) {
					// deliver when we arrive
					pm.deliver(this, curr.get(), time);
					available = true;
				}
			} else {
				// it is still available, go there as fast as possible
				rm.moveTo(this, curr.get(), time);
				if (rm.equalPosition(this, curr.get())) {
					// pickup customer
					pm.pickup(this, curr.get(), time);
				}
			}
		}
		
	}

	@Override
	public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
	    roadModel = Optional.of(pRoadModel);
	    pdpModel = Optional.of(pPdpModel);
	}

	@Override
	public Optional<Point> getPosition() {
		if (roadModel.get().containsObject(this)) {
			return Optional.of(roadModel.get().getPosition(this));
		}
		return Optional.absent();
	}

	@Override
	public void setCommDevice(CommDeviceBuilder builder) {
		if (RANGE >= 0) {
			builder.setMaxRange(RANGE);
		}
		device = Optional.of(builder
				.setReliability(reliability)
				.build());
	}

}
