/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package backtype.storm.utils;


import backtype.storm.Config;
import backtype.storm.generated.ClusterSummary;
import backtype.storm.generated.Nimbus;
import backtype.storm.generated.NimbusSummary;
import backtype.storm.nimbus.ILeaderElector;
import backtype.storm.nimbus.NimbusInfo;
import backtype.storm.security.auth.ThriftClient;
import backtype.storm.security.auth.ThriftConnectionType;
import clojure.lang.IFn;
import clojure.lang.PersistentArrayMap;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NimbusClient extends ThriftClient {
    private Nimbus.Client _client;
    private static final Logger LOG = LoggerFactory.getLogger(NimbusClient.class);


    public static NimbusClient getConfiguredClient(Map conf) {
        return getConfiguredClientAs(conf, null);
    }

    public static NimbusClient getConfiguredClientAs(Map conf, String asUser) {
        List<String> seeds = (List<String>) conf.get(Config.NIMBUS_SEEDS);
        if(seeds == null  || seeds.isEmpty()) {
            LOG.warn("config {} has no value. Failing over to deprecated config {}. ", Config.NIMBUS_SEEDS, Config.NIMBUS_HOST);
            seeds = Lists.newArrayList(conf.get(Config.NIMBUS_HOST) + ":" + conf.get(Config.NIMBUS_THRIFT_PORT));
        }

        for(String seed : seeds) {
            String[] split = seed.split(":");
            String host = split[0];
            int port = Integer.parseInt(split[1]);
            try {
                NimbusClient client = new NimbusClient(conf,host,port);
                ClusterSummary clusterInfo = client.getClient().getClusterInfo();
                List<NimbusSummary> nimbuses = clusterInfo.get_nimbuses();
                if(nimbuses != null) {
                    for(NimbusSummary nimbusSummary : nimbuses) {
                        if(nimbusSummary.is_isLeader()) {
                            return new NimbusClient(conf, nimbusSummary.get_host(), nimbusSummary.get_port(), null, asUser);
                        }
                    }
                }
                throw new RuntimeException("Found nimbuses " + nimbuses + " none of which is elected as leader, please try " +
                        "again after some time.");
            } catch (Exception e) {
                LOG.warn("Ignoring exception while trying to get leader nimbus info from " + seed, e);
            }
        }
        throw new RuntimeException("Could not find leader nimbus from seed hosts " + seeds +". " +
                "Did you specify a valid list of nimbus host:port for config " + Config.NIMBUS_SEEDS);
    }

    public NimbusClient(Map conf, String host, int port) throws TTransportException {
        this(conf, host, port, null);
    }

    public NimbusClient(Map conf, String host, int port, Integer timeout) throws TTransportException {
        super(conf, ThriftConnectionType.NIMBUS, host, port, timeout, null);
        _client = new Nimbus.Client(_protocol);
    }

    public NimbusClient(Map conf, String host, Integer port, Integer timeout, String asUser) throws TTransportException {
        super(conf, ThriftConnectionType.NIMBUS, host, port, timeout, asUser);
        _client = new Nimbus.Client(_protocol);
    }

    public NimbusClient(Map conf, String host) throws TTransportException {
        super(conf, ThriftConnectionType.NIMBUS, host, null, null, null);
        _client = new Nimbus.Client(_protocol);
    }

    public Nimbus.Client getClient() {
        return _client;
    }
}
