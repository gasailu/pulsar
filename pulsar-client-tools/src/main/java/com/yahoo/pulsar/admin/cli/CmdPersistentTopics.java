/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.pulsar.admin.cli;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.CommaParameterSplitter;
import com.yahoo.pulsar.client.admin.PulsarAdmin;
import com.yahoo.pulsar.client.admin.PulsarAdminException;
import com.yahoo.pulsar.client.admin.PersistentTopics;
import com.yahoo.pulsar.client.api.Message;
import com.yahoo.pulsar.client.impl.MessageIdImpl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

@Parameters(commandDescription = "Operations on persistent topics")
public class CmdPersistentTopics extends CmdBase {
    private final PersistentTopics persistentTopics;

    public CmdPersistentTopics(PulsarAdmin admin) {
        super("persistent", admin);
        persistentTopics = admin.persistentTopics();

        jcommander.addCommand("list", new ListCmd());
        jcommander.addCommand("permissions", new Permissions());
        jcommander.addCommand("grant-permission", new GrantPermissions());
        jcommander.addCommand("revoke-permission", new RevokePermissions());
        jcommander.addCommand("lookup", new Lookup());
        jcommander.addCommand("delete", new DeleteCmd());
        jcommander.addCommand("subscriptions", new ListSubscriptions());
        jcommander.addCommand("unsubscribe", new DeleteSubscription());
        jcommander.addCommand("stats", new GetStats());
        jcommander.addCommand("stats-internal", new GetInternalStats());
        jcommander.addCommand("partitioned-stats", new GetPartitionedStats());
        jcommander.addCommand("skip", new Skip());
        jcommander.addCommand("skip-all", new SkipAll());
        jcommander.addCommand("create-partitioned-topic", new CreatePartitionedCmd());
        jcommander.addCommand("get-partitioned-topic-metadata", new GetPartitionedTopicMetadataCmd());
        jcommander.addCommand("delete-partitioned-topic", new DeletePartitionedCmd());
        jcommander.addCommand("peek-messages", new PeekMessages());
        jcommander.addCommand("reset-cursor", new ResetCursor());
    }

    @Parameters(commandDescription = "Get the list of destinations under a namespace.")
    private class ListCmd extends CliCommand {
        @Parameter(description = "property/cluster/namespace\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String namespace = validateNamespace(params);
            print(persistentTopics.getList(namespace));
        }
    }

    @Parameters(commandDescription = "Grant a new permission to a client role on a single destination.")
    private class GrantPermissions extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = "--role", description = "Client role to which grant permissions", required = true)
        private String role;

        @Parameter(names = "--actions", description = "Actions to be granted (produce,consume)", required = true, splitter = CommaParameterSplitter.class)
        private List<String> actions;

        @Override
        void run() throws PulsarAdminException {
            String destination = validateDestination(params);
            persistentTopics.grantPermission(destination, role, getAuthActions(actions));
        }
    }

    @Parameters(commandDescription = "Revoke permissions on a destination \n "
            + "\t\t\t   Revoke permissions to a client role on a single destination. If the permission \n"
            + "\t\t\t   was not set at the destination level, but rather at the namespace level, this \n"
            + "\t\t\t   operation will return an error (HTTP status code 412).")
    private class RevokePermissions extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = "--role", description = "Client role to which revoke permissions", required = true)
        private String role;

        @Override
        void run() throws PulsarAdminException {
            String destination = validateDestination(params);
            persistentTopics.revokePermissions(destination, role);
        }
    }

    @Parameters(commandDescription = "Get the permissions on a destination\n"
            + "\t\t     Retrieve the effective permissions for a destination. These permissions are defined \n"
            + "\t\t     by the permissions set at the namespace level combined (union) with any eventual \n"
            + "\t\t     specific permission set on the destination.")
    private class Permissions extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String destination = validateDestination(params);
            print(persistentTopics.getPermissions(destination));
        }
    }

    @Parameters(commandDescription = "Lookup a destination from the current serving broker")
    private class Lookup extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/topic\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String destination = validateDestination(params);
            print(admin.lookups().lookupDestination(destination));
        }
    }

    @Parameters(commandDescription = "Create a partitioned topic. \n"
            + "\t\tThe partitioned topic has to be created before creating a producer on it.")
    private class CreatePartitionedCmd extends CliCommand {

        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-p",
                "--partitions" }, description = "Number of partitions for the topic", required = true)
        private int numPartitions;

        @Override
        void run() throws Exception {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.createPartitionedTopic(persistentTopic, numPartitions);
        }
    }

    @Parameters(commandDescription = "Get the partitioned topic metadata. \n"
            + "\t\tIf the topic is not created or is a non-partitioned topic, it returns empty topic with 0 partitions")
    private class GetPartitionedTopicMetadataCmd extends CliCommand {

        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws Exception {
            String persistentTopic = validatePersistentTopic(params);
            print(persistentTopics.getPartitionedTopicMetadata(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Delete a partitioned topic. \n"
            + "\t\tIt will also delete all the partitions of the topic if it exists.")
    private class DeletePartitionedCmd extends CliCommand {

        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws Exception {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.deletePartitionedTopic(persistentTopic);
        }
    }

    @Parameters(commandDescription = "Delete a topic. \n"
            + "\t\tThe topic cannot be deleted if there's any active subscription or producers connected to it.")
    private class DeleteCmd extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.delete(persistentTopic);
        }
    }

    @Parameters(commandDescription = "Get the list of subscriptions on the topic")
    private class ListSubscriptions extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws Exception {
            String persistentTopic = validatePersistentTopic(params);
            print(persistentTopics.getSubscriptions(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Delete a durable subscriber from a topic. \n"
            + "\t\tThe subscription cannot be deleted if there are any active consumers attached to it \n")
    private class DeleteSubscription extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-s", "--subscription" }, description = "Subscription to be deleted", required = true)
        private String subName;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.deleteSubscription(persistentTopic, subName);
        }
    }

    @Parameters(commandDescription = "Get the stats for the topic and its connected producers and consumers. \n"
            + "\t       All the rates are computed over a 1 minute window and are relative the last completed 1 minute period.")
    private class GetStats extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            print(persistentTopics.getStats(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Get the internal stats for the topic")
    private class GetInternalStats extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            print(persistentTopics.getInternalStats(persistentTopic));
        }
    }

    @Parameters(commandDescription = "Get the stats for the partitioned topic and its connected producers and consumers. \n"
            + "\t       All the rates are computed over a 1 minute window and are relative the last completed 1 minute period.")
    private class GetPartitionedStats extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination\n", required = true)
        private java.util.List<String> params;

        @Parameter(names = "--per-partition", description = "Get per partition stats")
        private boolean perPartition = false;

        @Override
        void run() throws Exception {
            String persistentTopic = validatePersistentTopic(params);
            print(persistentTopics.getPartitionedStats(persistentTopic, perPartition));
        }
    }

    @Parameters(commandDescription = "Skip all the messages for the subscription")
    private class SkipAll extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-s", "--subscription" }, description = "Subscription to be cleared", required = true)
        private String subName;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.skipAllMessages(persistentTopic, subName);
        }
    }

    @Parameters(commandDescription = "Skip some messages for the subscription")
    private class Skip extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-s",
                "--subscription" }, description = "Subscription to be skip messages on", required = true)
        private String subName;

        @Parameter(names = { "-n", "--count" }, description = "Number of messages to skip", required = true)
        private long numMessages;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            persistentTopics.skipMessages(persistentTopic, subName, numMessages);
        }
    }

    @Parameters(commandDescription = "Reset position for subscription to position closest to timestamp")
    private class ResetCursor extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-s",
                "--subscription" }, description = "Subscription to reset position on", required = true)
        private String subName;

        @Parameter(names = { "--time",
                "-t" }, description = "time in minutes to reset back to (or minutes, hours,days,weeks eg: 100m, 3h, 2d, 5w)", required = true)
        private String resetTimeStr;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            int resetBackTimeInMin = validateTimeString(resetTimeStr);
            long resetTimeInMillis = TimeUnit.MILLISECONDS.convert(resetBackTimeInMin, TimeUnit.MINUTES);
            // now - go back time
            long timestamp = System.currentTimeMillis() - resetTimeInMillis;
            persistentTopics.resetCursor(persistentTopic, subName, timestamp);
        }
    }

    @Parameters(commandDescription = "Peek some messages for the subscription")
    private class PeekMessages extends CliCommand {
        @Parameter(description = "persistent://property/cluster/namespace/destination", required = true)
        private java.util.List<String> params;

        @Parameter(names = { "-s",
                "--subscription" }, description = "Subscription to get messages from", required = true)
        private String subName;

        @Parameter(names = { "-n", "--count" }, description = "Number of messages (default 1)", required = false)
        private int numMessages = 1;

        @Override
        void run() throws PulsarAdminException {
            String persistentTopic = validatePersistentTopic(params);
            List<Message> messages = persistentTopics.peekMessages(persistentTopic, subName, numMessages);
            int position = 0;
            for (Message msg : messages) {
                if (++position != 1) {
                    System.out.println("-------------------------------------------------------------------------\n");
                }
                MessageIdImpl msgId = (MessageIdImpl) msg.getMessageId();
                System.out.println("Message ID: " + msgId.getLedgerId() + ":" + msgId.getEntryId());
                if (msg.getProperties().size() > 0) {
                    System.out.println("Properties:");
                    print(msg.getProperties());
                }
                ByteBuf data = Unpooled.wrappedBuffer(msg.getData());
                System.out.println(ByteBufUtil.prettyHexDump(data));
            }
        }
    }

    private static int validateTimeString(String s) {
        char last = s.charAt(s.length() - 1);
        String subStr = s.substring(0, s.length() - 1);
        switch (last) {
        case 'm':
        case 'M':
            return Integer.parseInt(subStr);

        case 'h':
        case 'H':
            return Integer.parseInt(subStr) * 60;

        case 'd':
        case 'D':
            return Integer.parseInt(subStr) * 24 * 60;

        case 'w':
        case 'W':
            return Integer.parseInt(subStr) * 7 * 24 * 60;

        default:
            return Integer.parseInt(s);
        }
    }
}
