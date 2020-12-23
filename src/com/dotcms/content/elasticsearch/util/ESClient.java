package com.dotcms.content.elasticsearch.util;
import static com.dotcms.repackage.elasticsearch.org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static com.dotcms.repackage.elasticsearch.org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.dotcms.cluster.bean.Server;
import com.dotcms.cluster.bean.ServerPort;
import com.dotcms.cluster.business.ClusterFactory;
import com.dotcms.cluster.business.ServerAPI;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.ConfigUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotcms.repackage.elasticsearch.org.elasticsearch.action.admin.indices.settings.UpdateSettingsRequest;
import com.dotcms.repackage.elasticsearch.org.elasticsearch.action.admin.indices.settings.UpdateSettingsResponse;
import com.dotcms.repackage.elasticsearch.org.elasticsearch.client.Client;
import com.dotcms.repackage.elasticsearch.org.elasticsearch.common.settings.ImmutableSettings;
import com.dotcms.repackage.elasticsearch.org.elasticsearch.node.Node;
import com.dotcms.elasticsearch.script.RelationshipSortOrderScriptFactory;
import com.liferay.util.FileUtil;

public class ESClient {

	private static Node _nodeInstance;
	final String syncMe = "esSync";

	public Client getClient() {
		if(_nodeInstance ==null){
			synchronized (syncMe) {
				if(_nodeInstance ==null){
					try{
						loadConfig();
						initNode();
					}catch (Exception e) {
						Logger.error(ESClient.class, "Could not initialize ES Node", e);
					}
				}
			}
		}
		return _nodeInstance.client();
	}

	private void initNode(){
		shutDownNode();

		String node_id = ConfigUtils.getServerId();
		_nodeInstance = nodeBuilder().
		        settings(ImmutableSettings.settingsBuilder().
		        put("name", node_id).
		        put("script.native.related.type", RelationshipSortOrderScriptFactory.class.getCanonicalName()).
		        build()).
		        build().
		        start();

		try {
			UpdateSettingsResponse resp=_nodeInstance.client().admin().indices().updateSettings(
			          new UpdateSettingsRequest().settings(
			                jsonBuilder().startObject()
			                     .startObject("index")
			                        .field("auto_expand_replicas","0-all")
			                     .endObject()
			               .endObject().string()
			        )).actionGet();
		}  catch (Exception e) {
			Logger.error(ESClient.class, "Unable to set ES property auto_expand_replicas.", e);
		}

		try {
		    // wait a bit while the node gets available for requests
            Thread.sleep(5000L);
        } catch (InterruptedException e) {

        }
	}

	public void shutDownNode(){
		if(_nodeInstance != null){
			_nodeInstance.close();
		}
	}

	private  void loadConfig(){
		Iterator<String> it = Config.getKeys();

		while(it.hasNext()){
			String key = it.next();

			if(key ==null) continue;

			if(key.startsWith("es.")){
				// if we already have a key, use it
				if(System.getProperty(key) == null){
					if(key.equalsIgnoreCase("es.path.data") || key.equalsIgnoreCase("es.path.work")){
                        String esPath = Config.getStringProperty(key);
                      if( new File(esPath).isAbsolute()){
                    	  System.setProperty(key,esPath);
                      }else
                        System.setProperty(key, FileUtil.getRealPath(esPath));
                    }
					else{
						System.setProperty(key, Config.getStringProperty(key));
					}

					//logger.debug( "Copying esdata folder..." );
				}
			}
		}
	}

	public void setClusterNode(Map<String, String> properties) throws Exception {

			String serverId = ConfigUtils.getServerId();

			ServerAPI serverAPI = APILocator.getServerAPI();
			Server currentServer = serverAPI.getServer(serverId);

			String storedBindAddr = (UtilMethods.isSet(currentServer.getHost()) && !currentServer.getHost().equals("localhost"))
					?currentServer.getHost():currentServer.getIpAddress();

			String bindAddr = properties!=null && UtilMethods.isSet(properties.get("BIND_ADDRESS")) ? properties.get("BIND_ADDRESS")
					:Config.getStringProperty("es.network.host", storedBindAddr);

			System.setProperty("es.network.host", bindAddr );

			currentServer.setHost(Config.getStringProperty("es.network.host", null));

			String transportTCPPort = properties!=null && UtilMethods.isSet(properties.get("ES_TRANSPORT_TCP_PORT")) ? properties.get("ES_TRANSPORT_TCP_PORT")
					:UtilMethods.isSet(currentServer.getEsTransportTcpPort())?currentServer.getEsTransportTcpPort().toString() : ClusterFactory.getNextAvailablePort(serverId, ServerPort.ES_TRANSPORT_TCP_PORT);

			System.setProperty("es.transport.tcp.port",  transportTCPPort);

			if(Config.getStringProperty("es.http.enabled", "false").equalsIgnoreCase("true")) {
				String httpPort = properties!=null &&   UtilMethods.isSet(properties.get("ES_HTTP_PORT")) ? properties.get("ES_HTTP_PORT")
						:UtilMethods.isSet(currentServer.getEsHttpPort()) ? currentServer.getEsHttpPort().toString()
						:ClusterFactory.getNextAvailablePort(serverId, ServerPort.ES_HTTP_PORT);

				System.setProperty("es.http.port",  httpPort);
				System.setProperty("es.http.enabled", "true");

				currentServer.setEsHttpPort(Integer.parseInt(httpPort));
			}

			System.setProperty("es.discovery.zen.ping.multicast.enabled",
					Config.getStringProperty("es.discovery.zen.ping.multicast.enabled", "false") );

			System.setProperty("es.discovery.zen.ping.timeout",
					Config.getStringProperty("es.discovery.zen.ping.timeout", "5s") );

			List<String> myself = new ArrayList<String>();
			myself.add(currentServer.getServerId());

			List<Server> aliveServers = serverAPI.getAliveServers(myself);

			currentServer.setEsTransportTcpPort(Integer.parseInt(transportTCPPort));

			aliveServers.add(currentServer);

			String initialHosts = "";

			int i=0;
			for (Server server : aliveServers) {
				if(i>0) {
					initialHosts += ", ";
				}

				if(UtilMethods.isSet(server.getHost()) && !server.getHost().equals("localhost")) {
					initialHosts += server.getHost() + "[" + server.getEsTransportTcpPort() + "]";
				} else {
					initialHosts += server.getIpAddress() + "[" + server.getEsTransportTcpPort() + "]";
				}

				i++;
			}

			if(initialHosts.equals("")) {
				if(bindAddr.equals("localhost")) {
					initialHosts += currentServer.getIpAddress() + "[" + transportTCPPort + "]";
				} else {
					initialHosts += bindAddr + "[" + transportTCPPort + "]";
				}
			}

			System.setProperty("es.discovery.zen.ping.unicast.hosts",initialHosts);

			loadConfig();
			initNode();

			try {
				serverAPI.updateServer(currentServer);
			} catch (DotDataException e) {
				Logger.error(ClusterFactory.class, "Error trying to update server. Server Id: " + currentServer.getServerId());
			}

	}


}
