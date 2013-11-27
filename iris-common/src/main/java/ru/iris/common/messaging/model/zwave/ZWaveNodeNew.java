package ru.iris.common.messaging.model.zwave;

import com.google.gson.annotations.Expose;
import ru.iris.common.devices.ZWaveDevice;

/**
 * IRISv2 Project
 * Author: Nikolay A. Viguro
 * WWW: iris.ph-systems.ru
 * E-Mail: nv@ph-systems.ru
 * Date: 19.11.13
 * Time: 11:34
 * License: GPL v3
 */
public class ZWaveNodeNew extends ZWaveNode {

    public ZWaveNodeNew set(ZWaveDevice device) {
        super.device = device;
        return this;
    }

    @Override
    public String toString() {
        return "ZWaveNodeNew { node: " + super.device.getInternalName() + " }";
    }
}