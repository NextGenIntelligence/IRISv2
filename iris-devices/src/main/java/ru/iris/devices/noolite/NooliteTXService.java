package ru.iris.devices.noolite;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.Expr;
import de.ailis.usb4java.libusb.Context;
import de.ailis.usb4java.libusb.DeviceHandle;
import de.ailis.usb4java.libusb.LibUsb;
import de.ailis.usb4java.libusb.LibUsbException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.iris.common.database.model.Log;
import ru.iris.common.database.model.devices.Device;
import ru.iris.common.database.model.devices.DeviceValue;
import ru.iris.common.messaging.JsonEnvelope;
import ru.iris.common.messaging.JsonMessaging;
import ru.iris.common.messaging.ServiceCheckEmitter;
import ru.iris.common.messaging.model.devices.SetDeviceLevelAdvertisement;
import ru.iris.common.messaging.model.devices.noolite.BindRXChannelAdvertisment;
import ru.iris.common.messaging.model.devices.noolite.BindTXChannelAdvertisment;
import ru.iris.common.messaging.model.devices.noolite.UnbindRXChannelAdvertisment;
import ru.iris.common.messaging.model.devices.noolite.UnbindTXChannelAdvertisment;
import ru.iris.common.messaging.model.service.ServiceStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * IRISv2 Project
 * Author: Nikolay A. Viguro
 * WWW: iris.ph-systems.ru
 * E-Mail: nv@ph-systems.ru
 * Date: 05.01.14
 * Time: 13:57
 * License: GPL v3
 */

public class NooliteTXService implements Runnable {

    private Logger log = LogManager.getLogger(NooliteTXService.class.getName());
    private boolean shutdown = false;
    private JsonMessaging messaging;
    private final Context context = new Context();
    private ServiceCheckEmitter serviceCheckEmitter;

    // Noolite PC USB TX HID
    static final int VENDOR_ID = 5824; //0x16c0;
    static final int PRODUCT_ID = 1503; //0x05df;

    public NooliteTXService() {
        Thread t = new Thread(this);
        t.setName("Noolite TX Service");
        t.start();
    }

    @Override
    public synchronized void run() {

        messaging = new JsonMessaging(UUID.randomUUID());

        serviceCheckEmitter = new ServiceCheckEmitter("Devices-NooliteTX");
        serviceCheckEmitter.setState(ServiceStatus.STARTUP);

        // Initialize the libusb context
        int result = LibUsb.init(context);
        if (result < 0)
            try {
                throw new LibUsbException("Unable to initialize libusb", result);
            } catch (LibUsbException e) {
                e.printStackTrace();
            }

        serviceCheckEmitter.setState(ServiceStatus.AVAILABLE);

        try {
            // Make sure we exit the wait loop if we receive shutdown signal.
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    shutdown = true;
                }
            }));

            JsonMessaging jsonMessaging = new JsonMessaging(UUID.randomUUID());

            jsonMessaging.subscribe("event.devices.noolite.setvalue");
            jsonMessaging.subscribe("event.devices.noolite.tx.bindchannel");
            jsonMessaging.subscribe("event.devices.noolite.tx.unbindchannel");

            jsonMessaging.start();

            while (!shutdown) {

                // Lets wait for 100 ms on json messages and if nothing comes then proceed to carry out other tasks.
                final JsonEnvelope envelope = jsonMessaging.receive(100);
                if (envelope != null) {
                    if (envelope.getObject() instanceof SetDeviceLevelAdvertisement) {

                        log.debug("Get SetDeviceLevel advertisement");

                        // We know of service advertisement
                        final SetDeviceLevelAdvertisement advertisement = envelope.getObject();
                        byte level = Byte.valueOf(advertisement.getValue());
                        Device device = Ebean.find(Device.class).where().eq("uuid", advertisement.getDeviceUUID()).findUnique();
                        int channel = Integer.valueOf(device.getValue("channel").getValue()) - 1;
						int channelView = channel + 1;

                        ByteBuffer buf = ByteBuffer.allocateDirect(8);
                        buf.put((byte) 0x30);

						DeviceValue dv = Ebean.find(DeviceValue.class).where().and(Expr.eq("device_id", device.getId()), Expr.eq("label", "Level")).findUnique();

						//if noolite device dimmer (user set)
						if (device.getValue("type") != null && device.getValue("type").getValue().contains("dimmer")) {

                            buf.put((byte) 6);
                            buf.put((byte) 1);

                            if (level > 99 || level == 99) {

								log.info("Turn on device on channel " + channelView);

								if (dv == null)
								{
									dv = new DeviceValue("Level", "100", "", "", device.getUuid(), false);
									device.updateValue(dv);
									Ebean.save(dv);
								}
								else
								{
									dv.setValue("100");
									Ebean.update(dv);
								}

								// log change
								/////////////////////////////////
								Log logChange = new Log("INFO", "Device is ON", device.getUuid());
								Ebean.save(logChange);
								/////////////////////////////////

                                level = 100;

							} else if (level < 0) {

								log.info("Turn off device on channel " + channelView);

								if (dv == null)
								{
									dv = new DeviceValue("Level", "0", "", "", device.getUuid(), false);
									device.updateValue(dv);
									Ebean.save(dv);
								}
								else
								{
									dv.setValue("0");
									Ebean.update(dv);
								}

								// log change
								/////////////////////////////////
								Log logChange = new Log("INFO", "Device is OFF", device.getUuid());
								Ebean.save(logChange);
								/////////////////////////////////

                                level = 0;

							} else {

								if (dv == null)
								{
									dv = new DeviceValue("Level", String.valueOf(level), "", "", device.getUuid(), false);
									device.updateValue(dv);
									Ebean.save(dv);
								}
								else
								{
									dv.setValue(String.valueOf(level));
									Ebean.update(dv);
								}

								// log change
								/////////////////////////////////
								Log logChange = new Log("INFO", "Device level set: " + level, device.getUuid());
								Ebean.save(logChange);
								/////////////////////////////////

								log.info("Setting device on channel " + channelView + " to level " + level);
							}

                            buf.position(5);
                            buf.put(level);

                            buf.position(4);
                            buf.put((byte) channel);

                            writeToHID(buf);
                        } else {
                            if (level < 0 || level == 0) {

								// turn off
								log.info("Turn off device on channel " + channelView);

								if (dv == null)
								{
									dv = new DeviceValue("Level", "0", "", "", device.getUuid(), false);
									device.updateValue(dv);
									Ebean.save(dv);
								}
								else
								{
									dv.setValue("0");
									Ebean.update(dv);
								}

								// log change
								/////////////////////////////////
								Log logChange = new Log("INFO", "Device is OFF", device.getUuid());
								Ebean.save(logChange);
								/////////////////////////////////

                                buf.put((byte) 0);

							} else {

								// turn on
								log.info("Turn on device on channel " + channelView);

								if (dv == null)
								{
									dv = new DeviceValue("Level", "100", "", "", device.getUuid(), false);
									device.updateValue(dv);
									Ebean.save(dv);
								}
								else
								{
									dv.setValue("100");
									Ebean.update(dv);
								}

								// log change
								/////////////////////////////////
								Log logChange = new Log("INFO", "Device is ON", device.getUuid());
								Ebean.save(logChange);
								/////////////////////////////////

                                buf.put((byte) 2);
                            }

                            buf.position(4);
                            buf.put((byte) channel);

                            writeToHID(buf);
                        }

                    } else if (envelope.getObject() instanceof BindTXChannelAdvertisment) {

                        log.debug("Get BindTXChannel advertisement");

                        final BindRXChannelAdvertisment advertisement = envelope.getObject();
                        Device device = Ebean.find(Device.class).where().eq("uuid", advertisement.getDeviceUUID()).findUnique();
                        int channel = Integer.valueOf(device.getValue("channel").getValue()) - 1;
						int channelView = channel + 1;

                        ByteBuffer buf = ByteBuffer.allocateDirect(8);
                        buf.put((byte) 0x30);
                        buf.put((byte) 15);
                        buf.position(4);
                        buf.put((byte) channel);

						log.info("Binding device to channel " + channelView);

                        writeToHID(buf);

                    } else if (envelope.getObject() instanceof UnbindTXChannelAdvertisment) {

                        log.debug("Get UnbindTXChannel advertisement");

                        final UnbindRXChannelAdvertisment advertisement = envelope.getObject();
                        Device device = Ebean.find(Device.class).where().eq("uuid", advertisement.getDeviceUUID()).findUnique();
                        int channel = Integer.valueOf(device.getValue("channel").getValue()) - 1;
						int channelView = channel + 1;

                        ByteBuffer buf = ByteBuffer.allocateDirect(8);
                        buf.put((byte) 0x30);
                        buf.put((byte) 9);
                        buf.position(4);
                        buf.put((byte) channel);

						log.info("Unbinding device from channel " + channelView);

                        writeToHID(buf);

                    } else if (envelope.getReceiverInstance() == null) {
                        // We received unknown broadcast message. Lets make generic log entry.
                        log.info("Received broadcast "
                                + " from " + envelope.getSenderInstance()
                                + " to " + envelope.getReceiverInstance()
                                + " at '" + envelope.getSubject()
                                + ": " + envelope.getObject());
                    } else {
                        // We received unknown request message. Lets make generic log entry.
                        log.info("Received request "
                                + " from " + envelope.getSenderInstance()
                                + " to " + envelope.getReceiverInstance()
                                + " at '" + envelope.getSubject()
                                + ": " + envelope.getObject());
                    }
                }
            }

            // Close JSON messaging.
            serviceCheckEmitter.setState(ServiceStatus.SHUTDOWN);
            jsonMessaging.close();
            messaging.close();
            LibUsb.exit(context);

        } catch (final Throwable t) {
            t.printStackTrace();
            log.error("Unexpected exception in NooliteTX", t);
        }
    }

    private void writeToHID(ByteBuffer command) throws IOException {

        DeviceHandle handle = LibUsb.openDeviceWithVidPid(context, VENDOR_ID, PRODUCT_ID);

        if (handle == null) {
            log.error("Noolite TX device not found!");
            shutdown = true;
            return;
        }

        if (LibUsb.kernelDriverActive(handle, 0) == 1)
            LibUsb.detachKernelDriver(handle, 0);

        int ret = LibUsb.setConfiguration(handle, 1);

        if (ret < 0) {
            log.error("Configuration error");
            LibUsb.close(handle);
            if (ret == LibUsb.ERROR_BUSY)
                log.error("Device busy");
            return;
        }

        LibUsb.claimInterface(handle, 0);

        log.debug("TX Buffer: " + command.get(0) + " " + command.get(1) + " " + command.get(2) + " " + command.get(3)
                + " " + command.get(4) + " " + command.get(5) + " " + command.get(6)
                + " " + command.get(7));

        //send
        LibUsb.controlTransfer(handle, LibUsb.REQUEST_TYPE_CLASS | LibUsb.RECIPIENT_INTERFACE, 0x9, 0x300, 0, command, 100);

        LibUsb.attachKernelDriver(handle, 0);
        LibUsb.close(handle);
    }
}
