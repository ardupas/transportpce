/*
 * Copyright © 2018 Orange Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.renderer.utils;

import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.transportpce.common.NetworkUtils;
import org.opendaylight.transportpce.common.device.DeviceTransactionManager;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929.Node1;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929.TerminationPoint1;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929.TerminationPoint1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.network.NodeKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev150608.TpId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev150608.network.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public class WaveLengthServiceUtils {

    private static InstanceIdentifierBuilder<TerminationPoint1> createTerminationPoint1IIDBuilder(String nodeId,
        String tpId) {
        return InstanceIdentifier
            .builder(Network.class, new NetworkKey(new NetworkId(NetworkUtils.OVERLAY_NETWORK_ID)))
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608.network
                .Node.class, new NodeKey(new NodeId(nodeId))).augmentation(
                org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev150608.Node1.class)
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev150608
                    .network.node.TerminationPoint.class,
                new TerminationPointKey(new TpId(tpId))).augmentation(TerminationPoint1.class);
    }

    public static void putTerminationPoint1ToDatastore(String nodeId, String tpId, TerminationPoint1 terminationPoint1,
        DeviceTransactionManager deviceTransactionManager)
        throws ExecutionException, InterruptedException {
        TerminationPoint1Builder terminationPoint1Builder = new TerminationPoint1Builder();
        TransactionUtils
            .writeTransaction(deviceTransactionManager, nodeId, LogicalDatastoreType.CONFIGURATION,
                createTerminationPoint1IIDBuilder(nodeId, tpId).build(), terminationPoint1);
    }

    public static TerminationPoint1 getTerminationPoint1FromDatastore(String nodeId, String tpId,
        DeviceTransactionManager deviceTransactionManager)
        throws ExecutionException, InterruptedException {
        InstanceIdentifier<TerminationPoint1> tpIID = createTerminationPoint1IIDBuilder(nodeId, tpId).build();
        return (TerminationPoint1) TransactionUtils
            .readTransaction(deviceTransactionManager, nodeId, LogicalDatastoreType.CONFIGURATION, tpIID);
    }

    private static InstanceIdentifier<Node1> createNode1IID(String nodeId) {
        return InstanceIdentifier
            .builder(Network.class, new NetworkKey(new NetworkId(NetworkUtils.OVERLAY_NETWORK_ID)))
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev150608
                .network.Node.class, new NodeKey(new NodeId(nodeId)))
            .augmentation(org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929
                .Node1.class)
            .build();
    }

    public static void putNode1ToDatastore(String nodeId, Node1 node1,
        DeviceTransactionManager deviceTransactionManager)
        throws ExecutionException, InterruptedException {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929.Node1>
            nodeIID = createNode1IID(nodeId);
        TransactionUtils
            .writeTransaction(deviceTransactionManager, nodeId,
                LogicalDatastoreType.CONFIGURATION, nodeIID, node1);
    }

    public static Node1 getNode1FromDatastore(String nodeId, DeviceTransactionManager deviceTransactionManager)
        throws ExecutionException, InterruptedException {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev170929.Node1>
            nodeIID = createNode1IID(nodeId);
        return (Node1) TransactionUtils
            .readTransaction(deviceTransactionManager, nodeId, LogicalDatastoreType.CONFIGURATION, nodeIID);
    }
}
