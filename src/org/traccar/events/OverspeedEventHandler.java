/*
 * Copyright 2016 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.events;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import org.traccar.BaseEventHandler;
import org.traccar.Context;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.Server;
import org.traccar.helper.Log;
import org.traccar.helper.UnitsConverter;

public class OverspeedEventHandler extends BaseEventHandler {

    public static final String ATTRIBUTE_SPEED_LIMIT = "speedLimit";

    private boolean checkGroups;
    private int suppressRepeated;

    public OverspeedEventHandler() {
        checkGroups = Context.getConfig().getBoolean("event.overspeed.groupsEnabled");
        suppressRepeated = Context.getConfig().getInteger("event.suppressRepeated", 60);
    }

    @Override
    protected Collection<Event> analyzePosition(Position position) {

        Device device = Context.getIdentityManager().getDeviceById(position.getDeviceId());
        if (device == null) {
            return null;
        }
        if (!Context.getDeviceManager().isLatestPosition(position) || !position.getValid()) {
            return null;
        }

        Collection<Event> events = new ArrayList<>();
        double speed = position.getSpeed();
        Double speedLimit = new Double(0);

        if (device.getAttributes().containsKey(ATTRIBUTE_SPEED_LIMIT)) {
            speedLimit = Double.parseDouble((String) device.getAttributes().get(ATTRIBUTE_SPEED_LIMIT));
        }
        if (speedLimit == 0 && checkGroups) {
            long groupId = device.getGroupId();
            while (groupId != 0) {
                if (Context.getDeviceManager().getGroupById(groupId).getAttributes()
                        .containsKey(ATTRIBUTE_SPEED_LIMIT)) {
                    speedLimit = Double.parseDouble((String) Context.getDeviceManager().getGroupById(groupId)
                            .getAttributes().get(ATTRIBUTE_SPEED_LIMIT));
                    if (speedLimit != 0) {
                        break;
                    }
                }
                if (Context.getDeviceManager().getGroupById(groupId) != null) {
                    groupId = Context.getDeviceManager().getGroupById(groupId).getGroupId();
                } else {
                    groupId = 0;
                }
            }
        }
        if (speedLimit == 0) {
            try {
                Server server = Context.getDataManager().getServer();
                if (server.getAttributes().containsKey(ATTRIBUTE_SPEED_LIMIT)) {
                    speedLimit = Double.parseDouble((String) server.getAttributes().get(ATTRIBUTE_SPEED_LIMIT));
                }
            } catch (SQLException error) {
                Log.warning(error);
            }
        }
        if (speedLimit != 0 && speed > UnitsConverter.knotsFromKph(speedLimit)) {
            try {
                if (Context.getDataManager().getLastEvents(
                        position.getDeviceId(), Event.TYPE_DEVICE_OVERSPEED, suppressRepeated).isEmpty()) {
                    events.add(new Event(Event.TYPE_DEVICE_OVERSPEED, position.getDeviceId(), position.getId()));
                }
            } catch (SQLException error) {
                Log.warning(error);
            }

        }
        return events;
    }

}
