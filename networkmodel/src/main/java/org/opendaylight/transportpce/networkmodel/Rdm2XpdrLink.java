/*
 * Copyright © 2016 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.networkmodel;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.transportpce.common.NetworkUtils;
import org.opendaylight.transportpce.networkmodel.util.LinkIdUtil;
import org.opendaylight.transportpce.networkmodel.util.OpenRoadmFactory;
import org.opendaylight.yang.gen.v1.http.org.opendaylight.transportpce.networkutils.rev170818.links.input.grouping.LinksInput;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev181130.Link1;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.topology.rev181130.Link1Builder;
import org.opendaylight.yang.gen.v1.http.org.openroadm.network.types.rev181130.OpenroadmLinkType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev180226.Network1;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev180226.Network1Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.topology.rev180226.networks.network.LinkBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


final class Rdm2XpdrLink {

    private static final Logger LOG = LoggerFactory.getLogger(Rdm2XpdrLink.class);

    public static boolean createXpdrRdmLinks(LinksInput linksInput, OpenRoadmFactory openRoadmFactory,
                                             DataBroker dataBroker) {
        String srcNode =
            new StringBuilder(linksInput.getXpdrNode()).append("-XPDR").append(linksInput.getXpdrNum()).toString();
        String srcTp = new StringBuilder("XPDR").append(linksInput.getXpdrNum()).append("-NETWORK")
            .append(linksInput.getNetworkNum()).toString();
        String destNode =
            new StringBuilder(linksInput.getRdmNode()).append("-SRG").append(linksInput.getSrgNum()).toString();
        String destTp = linksInput.getTerminationPointNum();

        Network topoNetowkLayer = createNetworkBuilder(srcNode, srcTp, destNode, destTp, false,
            openRoadmFactory).build();
        InstanceIdentifier.InstanceIdentifierBuilder<Network> nwIID = InstanceIdentifier.builder(Networks.class)
            .child(Network.class, new NetworkKey(new NetworkId(NetworkUtils.OVERLAY_NETWORK_ID)));
        WriteTransaction wrtx = dataBroker.newWriteOnlyTransaction();
        wrtx.merge(LogicalDatastoreType.CONFIGURATION, nwIID.build(), topoNetowkLayer);

        FluentFuture<? extends @NonNull CommitInfo> commit = wrtx.commit();

        try {
            commit.get();
            LOG.info("Post successful");
            return true;

        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Failed to create Xponder to Roadm link in the Topo layer ");
            return false;

        }
    }

    public static boolean createRdmXpdrLinks(LinksInput linksInput,
                                             OpenRoadmFactory openRoadmFactory, DataBroker dataBroker) {
        String srcNode =
            new StringBuilder(linksInput.getRdmNode()).append("-SRG").append(linksInput.getSrgNum()).toString();
        String srcTp = linksInput.getTerminationPointNum();
        String destNode =
            new StringBuilder(linksInput.getXpdrNode()).append("-XPDR").append(linksInput.getXpdrNum()).toString();
        String destTp = new StringBuilder("XPDR").append(linksInput.getXpdrNum()).append("-NETWORK")
            .append(linksInput.getNetworkNum()).toString();

        Network topoNetowkLayer = createNetworkBuilder(srcNode, srcTp, destNode, destTp, true,
            openRoadmFactory).build();
        InstanceIdentifier.InstanceIdentifierBuilder<Network> nwIID =
            InstanceIdentifier.builder(Networks.class).child(Network.class,
            new NetworkKey(new NetworkId(NetworkUtils.OVERLAY_NETWORK_ID)));
        WriteTransaction wrtx = dataBroker.newWriteOnlyTransaction();
        wrtx.merge(LogicalDatastoreType.CONFIGURATION, nwIID.build(), topoNetowkLayer);
        FluentFuture<? extends @NonNull CommitInfo> commit = wrtx.commit();
        try {
            commit.get();
            LOG.info("Post successful");
            return true;

        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Failed to create Xponder to Roadm link in the Topo layer ");
            return false;
        }
    }

    private static NetworkBuilder createNetworkBuilder(String srcNode, String srcTp, String destNode, String destTp,
                                                       boolean isXponderInput, OpenRoadmFactory openRoadmFactory) {
        Link1Builder lnk1bldr = new Link1Builder();
        org.opendaylight.yang.gen.v1.http.org.openroadm.common.network.rev181130.Link1Builder lnk2bldr
            = new org.opendaylight.yang.gen.v1.http.org.openroadm.common.network.rev181130.Link1Builder()
                .setLinkType(isXponderInput ? OpenroadmLinkType.XPONDERINPUT : OpenroadmLinkType.XPONDEROUTPUT)
                .setOppositeLink(LinkIdUtil.getOppositeLinkId(srcNode, srcTp, destNode, destTp));
        LinkBuilder linkBuilder = openRoadmFactory.createLink(srcNode, destNode, srcTp, destTp)
            .addAugmentation(Link1.class, lnk1bldr.build())
            .addAugmentation(
                org.opendaylight.yang.gen.v1.http.org.openroadm.common.network.rev181130.Link1.class,
                lnk2bldr.build());

        LOG.info("Link id in the linkbldr {}", linkBuilder.getLinkId());
        LOG.info("Link with oppo link {}", linkBuilder.augmentation(Link1.class));
        Network1Builder nwBldr1 = new Network1Builder().setLink(ImmutableList.of(linkBuilder.build()));
        NetworkId nwId = new NetworkId(NetworkUtils.OVERLAY_NETWORK_ID);
        NetworkBuilder nwBuilder = new NetworkBuilder()
            .setNetworkId(nwId)
            .withKey(new NetworkKey(nwId))
            .addAugmentation(Network1.class, nwBldr1.build());
        return nwBuilder;
    }

    private Rdm2XpdrLink() {
    }

}
