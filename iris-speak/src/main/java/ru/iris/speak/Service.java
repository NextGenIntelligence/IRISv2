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

package ru.iris.speak;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ru.iris.common.Config;
import ru.iris.common.helpers.Speak;
import ru.iris.speak.google.GoogleSpeakService;

public class Service extends Plugin
{

	private static final Logger LOGGER = LogManager.getLogger(Service.class);

	public Service(PluginWrapper wrapper)
	{
		super(wrapper);
	}

	@Override
	public void start()
	{
		LOGGER.info("[Plugin] iris-speak plugin started!");

		Config cfg = Config.getInstance();

		if (cfg.get("ttsEngine").equals("google"))
		{
			new GoogleSpeakService();
			Speak.say("Модуль синтеза речи Гугл запущен!");
		}
		else
		{
			LOGGER.info("No TTS feed specified in config file");
		}
	}

	@Override
	public void stop()
	{
		LOGGER.info("[Plugin] iris-speak plugin stopped!");
	}
}
