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

package ru.iris.events;

import com.avaje.ebean.Ebean;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import ru.iris.common.database.model.Event;
import ru.iris.common.messaging.JsonEnvelope;
import ru.iris.common.messaging.JsonMessaging;
import ru.iris.common.messaging.model.command.CommandAdvertisement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Author: Nikolay A. Viguro
 * Date: 19.11.13
 * Time: 11:25
 * License: GPL v3
 */

class EventsService implements Runnable
{

	private final Logger LOGGER = LogManager.getLogger(EventsService.class.getName());
	private boolean shutdown = false;

	public EventsService()
	{
		Thread t = new Thread(this);
		t.setName("Event Service");
		t.start();
	}

	@Override
	public synchronized void run()
	{

		try
		{

			// Make sure we exit the wait loop if we receive shutdown signal.
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					shutdown = true;
				}
			}));

			final JsonMessaging jsonMessaging = new JsonMessaging(UUID.randomUUID(), "events");

			// Initialize rhino engine
			Global global = new Global();
			Context cx = ContextFactory.getGlobal().enterContext();
			global.init(cx);
			Scriptable scope = cx.initStandardObjects(global);

			// Pass jsonmessaging instance to js engine
			ScriptableObject.putProperty(scope, "jsonMessaging", Context.javaToJS(jsonMessaging, scope));
			ScriptableObject.putProperty(scope, "out", Context.javaToJS(System.out, scope));

			// filter js files
			final FilenameFilter filter = new FilenameFilter()
			{
				public boolean accept(File dir, String name)
				{
					return name.endsWith(".js");
				}
			};

			// subscribe to anything
			jsonMessaging.subscribe("#");
			jsonMessaging.start();

			// load events from db
			List<Event> events = Ebean.find(Event.class).findList();

			while (!shutdown)
			{
				JsonEnvelope envelope = jsonMessaging.receive(100);

				if (envelope != null)
				{

					// Check command and launch script
					if (envelope.getObject() instanceof CommandAdvertisement)
					{
						CommandAdvertisement advertisement = envelope.getObject();
						LOGGER.info("Launch command script: " + advertisement.getScript());

						File jsFile = new File("./scripts/command/" + advertisement.getScript() + ".js");

						try
						{
							ScriptableObject.putProperty(scope, "commandParams", Context.javaToJS(advertisement.getData(), scope));
							cx.evaluateString(scope, FileUtils.readFileToString(jsFile), jsFile.toString(), 1, null);
						}
						catch (FileNotFoundException e)
						{
							LOGGER.error("Script file scripts/command/" + advertisement.getScript() + ".js not found!");
						}
						catch (Exception e)
						{
							LOGGER.error("Error in script scripts/command/" + advertisement.getScript() + ".js: " + e.toString());
							e.printStackTrace();
						}
					}
					else
					{

						for (Event event : events)
						{

							String[] subjects = event.getSubject().split(",");
							Set<String> set = new HashSet<>(Arrays.asList(subjects));

							if (wildCardMatch(set, envelope.getSubject()))
							{
								File jsFile = new File("./scripts/" + event.getScript());

								LOGGER.debug("Launch script: " + event.getScript());

								try
								{
									ScriptableObject.putProperty(scope, "advertisement", Context.javaToJS(envelope.getObject(), scope));
									cx.evaluateString(scope, FileUtils.readFileToString(jsFile), jsFile.toString(), 1, null);
								}
								catch (FileNotFoundException e)
								{
									LOGGER.error("Script file " + jsFile + " not found!");
								}
								catch (Exception e)
								{
									LOGGER.error("Error in script " + jsFile + ": " + e.toString());
									e.printStackTrace();
								}
							}
						}
					}
				}
			}

			// Close JSON messaging.
			jsonMessaging.close();

		}
		catch (final Throwable t)
		{
			t.printStackTrace();
			LOGGER.error("Unexpected exception in Events", t);
		}

	}

	private boolean wildCardMatch(Set<String> patterns, String text)
	{

		// add sentinel so don't need to worry about *'s at end of pattern
		for (String pattern : patterns)
		{
			text += '\0';
			pattern += '\0';

			int N = pattern.length();

			boolean[] states = new boolean[N + 1];
			boolean[] old = new boolean[N + 1];
			old[0] = true;

			for (int i = 0; i < text.length(); i++)
			{
				char c = text.charAt(i);
				states = new boolean[N + 1]; // initialized to false
				for (int j = 0; j < N; j++)
				{
					char p = pattern.charAt(j);

					// hack to handle *'s that match 0 characters
					if (old[j] && (p == '*'))
					{
						old[j + 1] = true;
					}

					if (old[j] && (p == c))
					{
						states[j + 1] = true;
					}
					if (old[j] && (p == '*'))
					{
						states[j] = true;
					}
					if (old[j] && (p == '*'))
					{
						states[j + 1] = true;
					}
				}
				old = states;
			}
			if (states[N])
			{
				return true;
			}
		}
		return false;
	}
}
