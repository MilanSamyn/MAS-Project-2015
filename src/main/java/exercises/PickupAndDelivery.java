package exercises;

import com.github.rinde.rinsim.geom.Point;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.TickListener;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.comm.CommDevice;
import com.github.rinde.rinsim.core.model.comm.CommDeviceBuilder;
import com.github.rinde.rinsim.core.model.comm.CommModel;
import com.github.rinde.rinsim.core.model.comm.CommUser;
import com.github.rinde.rinsim.core.model.comm.Message;
import com.github.rinde.rinsim.core.model.comm.MessageContents;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.PlaneRoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import exercises.TruckRenderer.Language;

public class PickupAndDelivery {

	
	private static final int TRUCK_CAPACITY = 10;
	
	private static final int NB_TRUCKS = 5;
	private static final int NB_PACKAGES = 2;
	
	private static final long SERVICE_DURATION = 60000;
	
	private static final double VEHICLE_SPEED_KMH = 50d;
	private static final Point MIN_POINT = new Point(0, 0);
	private static final Point MAX_POINT = new Point(10, 10);
	
	private PickupAndDelivery() {}


	/**
	* Starts the example.
	* @param args This is ignored.
	*/
	public static void main(String[] args) {
		run(false, Long.MAX_VALUE);
	}
	
	/**
	 * Run the example.
	 * @param testing if <code>true</code> turns on testing mode.
	 */
	public static Simulator run(boolean testing, final long endTime) {
		
	    final RoadModel roadModel = PlaneRoadModel.builder()
	    	.setMinPoint(MIN_POINT)
	    	.setMaxPoint(MAX_POINT)
	    	.setMaxSpeed(VEHICLE_SPEED_KMH)
	    	.build();
        
	    final DefaultPDPModel pdpModel = DefaultPDPModel.create();

	    final Simulator simulator = Simulator.builder()
	        .addModel(roadModel)
	        .addModel(pdpModel)
	        .addModel(CommModel.builder()
            .build())
	        .build();
			
	    final RandomGenerator rng = simulator.getRandomGenerator();
		
		// add a number of trucks on the road
		for (int i = 0; i < NB_TRUCKS; i++) {
			simulator.register(new Truck(roadModel.getRandomPosition(rng), TRUCK_CAPACITY, simulator.getRandomGenerator()));
		}
		
		// add a number of packages on the road
		for (int i = 0; i < NB_PACKAGES; i++) {
			simulator.register(new Package(roadModel.getRandomPosition(rng), 
					roadModel.getRandomPosition(rng), SERVICE_DURATION, SERVICE_DURATION, 1 + rng.nextInt(3), simulator.getRandomGenerator()));
		}
		
		simulator.addTickListener(new TickListener() {
			@Override
			public void tick(TimeLapse time) {
				if (time.getStartTime() > endTime) {
					simulator.stop();
				} else if (rng.nextDouble() < 0.007) {
					simulator.register(new Package(
							roadModel.getRandomPosition(rng), roadModel
							.getRandomPosition(rng), SERVICE_DURATION, SERVICE_DURATION,
							1 + rng.nextInt(3), simulator.getRandomGenerator()));
				}
			}

			@Override
			public void afterTick(TimeLapse timeLapse) {}
		});	

		final View.Builder view = View
				.create(simulator)
				.with(PlaneRoadModelRenderer.create())
				.with(RoadUserRenderer.builder()
						.addImageAssociation(
								Truck.class, "/graphics/perspective/empty-truck-64.png")
								.addImageAssociation(
										Package.class, "/graphics/perspective/deliverypackage.png")
						)
						.with(new TruckRenderer(Language.ENGLISH))
						.setTitleAppendix("Pickup And Delivery");

		if (testing) {
			view.enableAutoClose()
			.enableAutoPlay()
			.stopSimulatorAtTime(20 * 60 * 1000)
			.setSpeedUp(64);
		}

		view.show();
		return simulator;

	}
	
	/**
	 * Package class
	 */
	static class Package extends Parcel implements CommUser, TickListener {
		
		private Optional<RoadModel> roadModel;
		
		private boolean sheduled;
		
		Optional<CommDevice> device;	
		private static final double RANGE = 1000;
		private final double reliability;
		private final RandomGenerator rng;
		long lastReceiveTime = 0;
		
		Package(Point startPosition, Point pDestination, long pLoadingDuration, long pUnloadingDuration, double pMagnitude, RandomGenerator r) {
			super(pDestination, pLoadingDuration, TimeWindow.ALWAYS, pUnloadingDuration, TimeWindow.ALWAYS, pMagnitude);
			setStartPosition(startPosition);
			sheduled = false;
			rng = r;
			reliability = 1; //rng.nextDouble();
			
		    device = Optional.absent();
		}

		@Override
		public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
			roadModel = Optional.of(pRoadModel);
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

		@Override
		public void tick(TimeLapse timeLapse) {
			
			if (device.get().getUnreadCount() > 0 && !sheduled) {
				ImmutableList<Message> messages = device.get().getUnreadMessages();

				if (messages.size() == 1) { 
					if (messages.get(0).getContents().equals(Messages.I_CAN)) {
						device.get().send(Messages.PICK_ME_UP, messages.get(0).getSender());
						sheduled = true;
					}
				}
				else {
					int sender = 0;
					double distance = Point.distance(this.getPosition().get(), messages.get(0).getSender().getPosition().get());

					for (int i = 1; i < messages.size(); i++) {
						double newDistance = Point.distance(this.getPosition().get(), messages.get(i).getSender().getPosition().get());
						if (newDistance < distance) {
							sender = i;
							distance = newDistance;
						}
					}
					device.get().send(Messages.PICK_ME_UP, messages.get(sender).getSender());
					sheduled = true;
				}
				
			} else if (device.get().getReceivedCount() == 0) {
				device.get().broadcast(Messages.WHO_CAN_PICK_ME_UP);
			}
			
		}

		@Override
		public void afterTick(TimeLapse timeLapse) {}
		
	}
	
	enum Messages implements MessageContents {
		WHO_CAN_PICK_ME_UP, I_CAN, PICK_ME_UP;
	}
	
}
