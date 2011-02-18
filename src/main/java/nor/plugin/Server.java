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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import nor.http.server.proxyserver.Router;
import nor.util.log.Logger;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.LocalPortForwarder;
import ch.ethz.ssh2.Session;

public class Server implements Closeable{

	private Connection con;
	private List<ForwarderInfo> infos = new ArrayList<ForwarderInfo>();

	private static final Random Rand = new Random(System.currentTimeMillis());
	private static final int START_PORT = 49152;
	private static final int END_PORT = 65535;

	private static final Logger LOGGER = Logger.getLogger(Server.class);

	//============================================================================
	//  Properties
	//============================================================================
	public String host;
	public int port;
	public String username;
	public String privateKey;
	public String password;

	public List<Forwarding> forwardings;

	//============================================================================
	//  Public methods
	//============================================================================
	@Override
	public void close() throws IOException {
		LOGGER.entering("close");

		for(final ForwarderInfo f : this.infos){

			f.close();

		}

		this.con.close();
		LOGGER.exiting("close");
	}

	//============================================================================
	//  Package private methods
	//============================================================================
	boolean connect() throws IOException{
		LOGGER.entering("connect");

		this.con = new Connection(this.host, this.port);
		this.con.connect();

		boolean auth;
		if(this.privateKey != null){

			auth = con.authenticateWithPublicKey(this.username, new File(this.privateKey), this.password);

		}else{

			if(this.password == null){

				// Open password window;

			}
			auth = con.authenticateWithPassword(this.username, this.password);

		}

		LOGGER.exiting("connect", auth);
		return auth;
	}

	boolean isAlive(){
		LOGGER.entering("isAlive");

		boolean res;
		try{

			final Session s = this.con.openSession();
			s.close();

			res = true;

		}catch(final IOException e){

			res = false;

		}

		LOGGER.exiting("isAlive", res);
		return res;
	}

	boolean reconnect() throws IOException{
		LOGGER.entering("reconnect");

		this.close();

		final boolean res = this.connect();
		if(res){

			for(final ForwarderInfo info : this.infos){

				info.forwarder = con.createLocalPortForwarder(info.localPort, info.host, info.port);

			}

		}

		LOGGER.exiting("reconnect", res);
		return res;
	}

	// Return a regex matching to this portforwarding settings.
	String createForwarders(final Router router) throws MalformedURLException{
		LOGGER.entering("createForwarders", router);

		String regex = "";
		if(this.forwardings.size() != 0){

			final StringBuilder res = new StringBuilder();
			res.append("(");

			for(final Forwarding f : this.forwardings){

				int localPort = 0;
				LocalPortForwarder forwarder = null;
				for(int tcout = 0; tcout != 5 && forwarder == null; ++tcout){

					try{

						localPort = Rand.nextInt(END_PORT - START_PORT) + START_PORT;
						forwarder = con.createLocalPortForwarder(localPort, f.host, f.port);

					}catch(final IOException e){

						LOGGER.catched(Level.FINE, "init", e);
						continue;

					}

				}
				if(forwarder != null){

					this.infos.add(new ForwarderInfo(forwarder, localPort, f.host, f.port));
					router.put(f.pattern, "127.0.0.1", localPort);

					LOGGER.info("loadConfig", "Add a forwarding {0} to {1}:{2}", f.pattern, f.host, f.port);

					res.append(f.pattern);
					res.append("|");

				}

			}

			if(this.infos.size() != 0){

				res.setLength(res.length()-1);
				res.append(")(.*)");
				regex = res.toString();

			}

		}

		LOGGER.exiting("createForwarders", regex);
		return regex;
	}

	//============================================================================
	//  Private inner classes
	//============================================================================
	private class ForwarderInfo implements Closeable{

		//========================================================================
		//  Properties
		//========================================================================
		public LocalPortForwarder forwarder;
		public int localPort;
		public String host;
		public int port;

		//========================================================================
		//  Constructors
		//========================================================================
		public ForwarderInfo(final LocalPortForwarder f, final int localPort, final String host, final int port){

			this.forwarder = f;
			this.localPort = localPort;
			this.host = host;
			this.port = port;

		}

		//========================================================================
		//  Public methods
		//========================================================================
		@Override
		public void close() throws IOException{

			this.forwarder.close();

		}

	}

}
