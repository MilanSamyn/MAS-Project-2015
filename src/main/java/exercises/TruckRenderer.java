/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package exercises;

import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;

import com.github.rinde.rinsim.core.model.ModelProvider;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleState;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.ui.renderers.ModelRenderer;
import com.github.rinde.rinsim.ui.renderers.ViewPort;
import com.github.rinde.rinsim.ui.renderers.ViewRect;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon 
 * 
 */
public class TruckRenderer implements ModelRenderer {

	enum Language {
		DUTCH("LADEN", "UITLADEN"), ENGLISH("LOAD", "UNLOAD");

		final String load;
		final String unload;

		Language(String s1, String s2) {
			load = s1;
			unload = s2;
		}
	}

	Optional<RoadModel> rm;
	Optional<DefaultPDPModel> pm;
	Language lang;

	TruckRenderer(Language l) {
		lang = l;
		rm = Optional.absent();
		pm = Optional.absent();
	}

	@Override
	public void registerModelProvider(ModelProvider mp) {
		rm = Optional.fromNullable(mp.tryGetModel(RoadModel.class));
		pm = Optional.fromNullable(mp.tryGetModel(DefaultPDPModel.class));
	}

	@Override
	public void renderStatic(GC gc, ViewPort vp) {}

	@Override
	public void renderDynamic(GC gc, ViewPort vp, long time) {
		final Set<Truck> trucks = rm.get().getObjectsOfType(Truck.class);
		synchronized (trucks) {
			for (final Truck t : trucks) {
				final Point p = rm.get().getPosition(t);
				final int x = vp.toCoordX(p.x) - 5;
				final int y = vp.toCoordY(p.y) - 30;

				final VehicleState vs = pm.get().getVehicleState(t);

				String text = null;
				final int size = (int) pm.get().getContentsSize(t);
				if (vs == VehicleState.DELIVERING) {
					text = lang.unload;
				} else if (vs == VehicleState.PICKING_UP) {
					text = lang.load;
				} else if (size > 0) {
					text = size + "";
				}

				if (text != null) {
					final org.eclipse.swt.graphics.Point extent = gc.textExtent(text);

					gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_DARK_BLUE));
					gc.fillRoundRectangle(x - (extent.x / 2), y - (extent.y / 2),
							extent.x + 2, extent.y + 2, 5, 5);
					gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));

					gc.drawText(text, x - (extent.x / 2) + 1, y - (extent.y / 2) + 1,
							true);
				}
			}
		}
	}

	@Nullable
	@Override
	public ViewRect getViewRect() {
		return null;
	}
}
