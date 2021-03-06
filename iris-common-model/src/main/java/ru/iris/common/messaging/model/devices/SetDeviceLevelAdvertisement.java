/*
 * Copyright 2012-2014 Nikolay A. Viguro
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.iris.common.messaging.model.devices;

import ru.iris.common.messaging.model.Advertisement;

public class SetDeviceLevelAdvertisement extends Advertisement
{

	/**
	 * Device UUID
	 */
	protected String deviceUUID;

	/**
	 * Label, what value we want change.
	 */
	protected String label;

	/**
	 * Label value.
	 */
	protected String value;

	public SetDeviceLevelAdvertisement() {
	}

	public SetDeviceLevelAdvertisement(String deviceUUID, String label, String value)
	{
		this.label = label;
		this.value = value;
		this.deviceUUID = deviceUUID;
	}

	public String getLabel()
	{
		return label;
	}

	public void setLabel(String label)
	{
		this.label = label;
	}

	public String getValue()
	{
		return value;
	}

	public void setValue(String value)
	{
		this.value = value;
	}

	public String getDeviceUUID()
	{
		return deviceUUID;
	}

	public void setDeviceUUID(String deviceUUID)
	{
		this.deviceUUID = deviceUUID;
	}

	@Override
	public String toString()
	{
		return "SetDeviceLevelAdvertisement { UUID: " + deviceUUID + ", label: " + label + ", value: " + value + " }";
	}
}
