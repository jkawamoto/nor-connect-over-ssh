/*
 *  Copyright (C) 2011 Junpei Kawamoto
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package nor.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.MatchResult;

import net.arnx.jsonic.JSON;
import nor.core.Nor;
import nor.core.plugin.PluginAdapter;
import nor.core.proxy.filter.FilterRegister;
import nor.core.proxy.filter.ResponseFilter;
import nor.core.proxy.filter.ResponseFilterAdapter;
import nor.http.HttpResponse;
import nor.http.Status;
import nor.http.server.proxyserver.Router;
import nor.util.io.Stream;
import nor.util.log.Logger;

public class ConnectOverSSH extends PluginAdapter{

	private final List<Server> servers = new ArrayList<Server>();
	private final List<ErrorChecker> checkers = new ArrayList<ErrorChecker>();

	private static final Logger LOGGER = Logger.getLogger(ConnectOverSSH.class);

	//============================================================================
	//  public methods
	//============================================================================
	@Override
	public void init(final File common, final File local) throws IOException {
		LOGGER.entering("init", common, local);

		if(!common.exists()){

			final InputStream in = this.getClass().getResourceAsStream("default.conf");
			final OutputStream out = new FileOutputStream(common);

			Stream.copy(in, out);

			out.close();
			in.close();

		}
		final Router router = Nor.getRouter();
		this.loadConfig(common, router);
		if(local.exists()){

			this.loadConfig(local, router);

		}

		LOGGER.exiting("init");
	}

	@Override
	public void close() throws IOException {
		LOGGER.entering("close");

		for(final Server s : this.servers){

			s.close();

		}


		LOGGER.exiting("close");
	}

	@Override
	public ResponseFilter[] responseFilters() {
		LOGGER.entering("responseFilters");

		ResponseFilter[] res = null;
		final int size = this.checkers.size();
		if(size != 0){

			res = this.checkers.toArray(new ResponseFilter[size]);

		}

		LOGGER.exiting("reponseFilters", res);
		return res;
	}

	//============================================================================
	//  private methods
	//============================================================================
	private void loadConfig(final File file, final Router router) throws IOException{

		final Reader r = new FileReader(file);
		final Config conf = JSON.decode(r, Config.class);
		r.close();

		for(final Server server : conf.servers){

			if(!server.connect()){

				LOGGER.warning("loadConfig", "Connection to {0} is failed", server.host);
				continue;

			}

			LOGGER.info("loadConfig", "Connection to {0} is established", server.host);
			final String regex = server.createForwarders(router);
			this.servers.add(server);
			this.checkers.add(new ErrorChecker(regex, server));

		}

	}

	//============================================================================
	//  private inner classes
	//============================================================================
	private class ErrorChecker extends ResponseFilterAdapter{

		private final Server server;

		public ErrorChecker(final String urlRegex, final Server server) {
			super(urlRegex, "");

			this.server = server;

		}

		@Override
		public void update(final HttpResponse msg,
				final MatchResult url, final MatchResult cType, final FilterRegister register) {

			LOGGER.entering(ErrorChecker.class, "update", msg, url, cType, register);

			if(msg.getCode() >= 500 && !this.server.isAlive()){

				LOGGER.info(ErrorChecker.class,
						"update", "SSH connection to {0}:{1} is broken. Reconnecting...", this.server.host, this.server.port);

				try {

					this.server.reconnect();
					LOGGER.info(ErrorChecker.class,
							"update", "SSH connection to {0}:{1} reconnected", this.server.host, this.server.port);

					msg.setStatus(Status.ServiceUnavailable);
					msg.setBody(null);

				} catch (final IOException e) {

					LOGGER.catched(Level.FINE, ErrorChecker.class, "update", e);
					LOGGER.warning(ErrorChecker.class,
							"update", "Could not reconnect to {0}:{1}", this.server.host, this.server.port);

				}

			}

			LOGGER.exiting(ErrorChecker.class, "update");
		}

	}

}
