/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.coordinator.group;

import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.message.JoinGroupRequestData.JoinGroupRequestProtocol;
import org.apache.kafka.common.message.JoinGroupRequestData.JoinGroupRequestProtocolCollection;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.MockTime;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.coordinator.group.classic.ClassicGroup;
import org.apache.kafka.coordinator.group.classic.ClassicGroupMember;
import org.apache.kafka.coordinator.group.classic.ClassicGroupState;
import org.apache.kafka.coordinator.group.consumer.ConsumerGroupMember;
import org.apache.kafka.coordinator.group.consumer.MemberState;
import org.apache.kafka.coordinator.group.consumer.TopicMetadata;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupCurrentMemberAssignmentKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupCurrentMemberAssignmentValue;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupMemberMetadataKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupMemberMetadataValue;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupMetadataKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupMetadataValue;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupPartitionMetadataKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupPartitionMetadataValue;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupTargetAssignmentMemberKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupTargetAssignmentMemberValue;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupTargetAssignmentMetadataKey;
import org.apache.kafka.coordinator.group.generated.ConsumerGroupTargetAssignmentMetadataValue;
import org.apache.kafka.coordinator.group.generated.GroupMetadataKey;
import org.apache.kafka.coordinator.group.generated.GroupMetadataValue;
import org.apache.kafka.coordinator.group.generated.OffsetCommitKey;
import org.apache.kafka.coordinator.group.generated.OffsetCommitValue;
import org.apache.kafka.coordinator.group.metrics.GroupCoordinatorMetricsShard;
import org.apache.kafka.server.common.ApiMessageAndVersion;
import org.apache.kafka.server.common.MetadataVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.kafka.coordinator.group.Assertions.assertRecordEquals;
import static org.apache.kafka.coordinator.group.AssignmentTestUtil.mkOrderedAssignment;
import static org.apache.kafka.coordinator.group.AssignmentTestUtil.mkOrderedTopicAssignment;
import static org.apache.kafka.coordinator.group.AssignmentTestUtil.mkTopicAssignment;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newCurrentAssignmentRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newCurrentAssignmentTombstoneRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newGroupEpochRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newGroupEpochTombstoneRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newGroupSubscriptionMetadataRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newGroupSubscriptionMetadataTombstoneRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newMemberSubscriptionRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newMemberSubscriptionTombstoneRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newTargetAssignmentEpochRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newTargetAssignmentEpochTombstoneRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newTargetAssignmentRecord;
import static org.apache.kafka.coordinator.group.CoordinatorRecordHelpers.newTargetAssignmentTombstoneRecord;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class CoordinatorRecordHelpersTest {

    @Test
    public void testNewMemberSubscriptionRecord() {
        List<ConsumerGroupMemberMetadataValue.ClassicProtocol> protocols = new ArrayList<>();
        protocols.add(new ConsumerGroupMemberMetadataValue.ClassicProtocol()
            .setName("range")
            .setMetadata(new byte[0]));

        ConsumerGroupMember member = new ConsumerGroupMember.Builder("member-id")
            .setInstanceId("instance-id")
            .setRackId("rack-id")
            .setRebalanceTimeoutMs(5000)
            .setClientId("client-id")
            .setClientHost("client-host")
            .setSubscribedTopicNames(Arrays.asList("foo", "zar", "bar"))
            .setSubscribedTopicRegex("regex")
            .setServerAssignorName("range")
            .setClassicMemberMetadata(new ConsumerGroupMemberMetadataValue.ClassicMemberMetadata()
                .setSupportedProtocols(protocols))
            .build();

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupMemberMetadataKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 5),
            new ApiMessageAndVersion(
                new ConsumerGroupMemberMetadataValue()
                    .setInstanceId("instance-id")
                    .setRackId("rack-id")
                    .setRebalanceTimeoutMs(5000)
                    .setClientId("client-id")
                    .setClientHost("client-host")
                    .setSubscribedTopicNames(Arrays.asList("bar", "foo", "zar"))
                    .setSubscribedTopicRegex("regex")
                    .setServerAssignor("range")
                    .setClassicMemberMetadata(new ConsumerGroupMemberMetadataValue.ClassicMemberMetadata()
                        .setSupportedProtocols(protocols)),
                (short) 0));

        assertEquals(expectedRecord, newMemberSubscriptionRecord(
            "group-id",
            member
        ));
    }

    @Test
    public void testNewMemberSubscriptionTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupMemberMetadataKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 5
            ),
            null);

        assertEquals(expectedRecord, newMemberSubscriptionTombstoneRecord(
            "group-id",
            "member-id"
        ));
    }

    @Test
    public void testNewGroupSubscriptionMetadataRecord() {
        Uuid fooTopicId = Uuid.randomUuid();
        Uuid barTopicId = Uuid.randomUuid();
        Map<String, TopicMetadata> subscriptionMetadata = new LinkedHashMap<>();

        subscriptionMetadata.put("foo", new TopicMetadata(
            fooTopicId,
            "foo",
            10,
            mkMapOfPartitionRacks(10)
        ));
        subscriptionMetadata.put("bar", new TopicMetadata(
            barTopicId,
            "bar",
            20,
            mkMapOfPartitionRacks(20)
        ));

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupPartitionMetadataKey()
                    .setGroupId("group-id"),
                (short) 4
            ),
            new ApiMessageAndVersion(
                new ConsumerGroupPartitionMetadataValue()
                    .setTopics(Arrays.asList(
                        new ConsumerGroupPartitionMetadataValue.TopicMetadata()
                            .setTopicId(fooTopicId)
                            .setTopicName("foo")
                            .setNumPartitions(10)
                            .setPartitionMetadata(mkListOfPartitionRacks(10)),
                        new ConsumerGroupPartitionMetadataValue.TopicMetadata()
                            .setTopicId(barTopicId)
                            .setTopicName("bar")
                            .setNumPartitions(20)
                            .setPartitionMetadata(mkListOfPartitionRacks(20)))),
                (short) 0));

        assertRecordEquals(expectedRecord, newGroupSubscriptionMetadataRecord(
            "group-id",
            subscriptionMetadata
        ));
    }

    @Test
    public void testNewGroupSubscriptionMetadataTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupPartitionMetadataKey()
                    .setGroupId("group-id"),
                (short) 4
            ),
            null);

        assertEquals(expectedRecord, newGroupSubscriptionMetadataTombstoneRecord(
            "group-id"
        ));
    }

    @Test
    public void testEmptyPartitionMetadataWhenRacksUnavailableGroupSubscriptionMetadataRecord() {
        Uuid fooTopicId = Uuid.randomUuid();
        Uuid barTopicId = Uuid.randomUuid();
        Map<String, TopicMetadata> subscriptionMetadata = new LinkedHashMap<>();

        subscriptionMetadata.put("foo", new TopicMetadata(
            fooTopicId,
            "foo",
            10,
            Collections.emptyMap()
        ));
        subscriptionMetadata.put("bar", new TopicMetadata(
            barTopicId,
            "bar",
            20,
            Collections.emptyMap()
        ));

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupPartitionMetadataKey()
                    .setGroupId("group-id"),
                (short) 4
            ),
            new ApiMessageAndVersion(
                new ConsumerGroupPartitionMetadataValue()
                    .setTopics(Arrays.asList(
                        new ConsumerGroupPartitionMetadataValue.TopicMetadata()
                            .setTopicId(fooTopicId)
                            .setTopicName("foo")
                            .setNumPartitions(10)
                            .setPartitionMetadata(Collections.emptyList()),
                        new ConsumerGroupPartitionMetadataValue.TopicMetadata()
                            .setTopicId(barTopicId)
                            .setTopicName("bar")
                            .setNumPartitions(20)
                            .setPartitionMetadata(Collections.emptyList()))),
                (short) 0));

        assertRecordEquals(expectedRecord, newGroupSubscriptionMetadataRecord(
            "group-id",
            subscriptionMetadata
        ));
    }

    @Test
    public void testNewGroupEpochRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupMetadataKey()
                    .setGroupId("group-id"),
                (short) 3),
            new ApiMessageAndVersion(
                new ConsumerGroupMetadataValue()
                    .setEpoch(10),
                (short) 0));

        assertEquals(expectedRecord, newGroupEpochRecord(
            "group-id",
            10
        ));
    }

    @Test
    public void testNewGroupEpochTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupMetadataKey()
                    .setGroupId("group-id"),
                (short) 3),
            null);

        assertEquals(expectedRecord, newGroupEpochTombstoneRecord(
            "group-id"
        ));
    }

    @Test
    public void testNewTargetAssignmentRecord() {
        Uuid topicId1 = Uuid.randomUuid();
        Uuid topicId2 = Uuid.randomUuid();

        Map<Uuid, Set<Integer>> partitions = mkOrderedAssignment(
            mkTopicAssignment(topicId1, 11, 12, 13),
            mkTopicAssignment(topicId2, 21, 22, 23)
        );

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMemberKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 7),
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMemberValue()
                    .setTopicPartitions(Arrays.asList(
                        new ConsumerGroupTargetAssignmentMemberValue.TopicPartition()
                            .setTopicId(topicId1)
                            .setPartitions(Arrays.asList(11, 12, 13)),
                        new ConsumerGroupTargetAssignmentMemberValue.TopicPartition()
                            .setTopicId(topicId2)
                            .setPartitions(Arrays.asList(21, 22, 23)))),
                (short) 0));

        assertEquals(expectedRecord, newTargetAssignmentRecord(
            "group-id",
            "member-id",
            partitions
        ));
    }

    @Test
    public void testNewTargetAssignmentTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMemberKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 7),
            null);

        assertEquals(expectedRecord, newTargetAssignmentTombstoneRecord(
            "group-id",
            "member-id"
        ));
    }

    @Test
    public void testNewTargetAssignmentEpochRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMetadataKey()
                    .setGroupId("group-id"),
                (short) 6),
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMetadataValue()
                    .setAssignmentEpoch(10),
                (short) 0));

        assertEquals(expectedRecord, newTargetAssignmentEpochRecord(
            "group-id",
            10
        ));
    }

    @Test
    public void testNewTargetAssignmentEpochTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupTargetAssignmentMetadataKey()
                    .setGroupId("group-id"),
                (short) 6),
            null);

        assertEquals(expectedRecord, newTargetAssignmentEpochTombstoneRecord(
            "group-id"
        ));
    }

    @Test
    public void testNewCurrentAssignmentRecord() {
        Uuid topicId1 = Uuid.randomUuid();
        Uuid topicId2 = Uuid.randomUuid();

        Map<Uuid, Set<Integer>> assigned = mkOrderedAssignment(
            mkOrderedTopicAssignment(topicId1, 11, 12, 13),
            mkOrderedTopicAssignment(topicId2, 21, 22, 23)
        );

        Map<Uuid, Set<Integer>> revoking = mkOrderedAssignment(
            mkOrderedTopicAssignment(topicId1, 14, 15, 16),
            mkOrderedTopicAssignment(topicId2, 24, 25, 26)
        );

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupCurrentMemberAssignmentKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 8),
            new ApiMessageAndVersion(
                new ConsumerGroupCurrentMemberAssignmentValue()
                    .setState(MemberState.UNREVOKED_PARTITIONS.value())
                    .setMemberEpoch(22)
                    .setPreviousMemberEpoch(21)
                    .setAssignedPartitions(Arrays.asList(
                        new ConsumerGroupCurrentMemberAssignmentValue.TopicPartitions()
                            .setTopicId(topicId1)
                            .setPartitions(Arrays.asList(11, 12, 13)),
                        new ConsumerGroupCurrentMemberAssignmentValue.TopicPartitions()
                            .setTopicId(topicId2)
                            .setPartitions(Arrays.asList(21, 22, 23))))
                    .setPartitionsPendingRevocation(Arrays.asList(
                        new ConsumerGroupCurrentMemberAssignmentValue.TopicPartitions()
                            .setTopicId(topicId1)
                            .setPartitions(Arrays.asList(14, 15, 16)),
                        new ConsumerGroupCurrentMemberAssignmentValue.TopicPartitions()
                            .setTopicId(topicId2)
                            .setPartitions(Arrays.asList(24, 25, 26)))),
                (short) 0));

        assertEquals(expectedRecord, newCurrentAssignmentRecord(
            "group-id",
            new ConsumerGroupMember.Builder("member-id")
                .setState(MemberState.UNREVOKED_PARTITIONS)
                .setMemberEpoch(22)
                .setPreviousMemberEpoch(21)
                .setAssignedPartitions(assigned)
                .setPartitionsPendingRevocation(revoking)
                .build()
        ));
    }

    @Test
    public void testNewCurrentAssignmentTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new ConsumerGroupCurrentMemberAssignmentKey()
                    .setGroupId("group-id")
                    .setMemberId("member-id"),
                (short) 8),
            null);

        assertEquals(expectedRecord, newCurrentAssignmentTombstoneRecord(
            "group-id",
            "member-id"
        ));
    }

    private static Stream<Arguments> metadataToExpectedGroupMetadataValue() {
        return Stream.of(
            Arguments.arguments(MetadataVersion.IBP_0_10_0_IV0, (short) 0),
            Arguments.arguments(MetadataVersion.IBP_1_1_IV0, (short) 1),
            Arguments.arguments(MetadataVersion.IBP_2_2_IV0, (short) 2),
            Arguments.arguments(MetadataVersion.IBP_3_5_IV0, (short) 3)
        );
    }

    @ParameterizedTest
    @MethodSource("metadataToExpectedGroupMetadataValue")
    public void testNewGroupMetadataRecord(
        MetadataVersion metadataVersion,
        short expectedGroupMetadataValueVersion
    ) {
        Time time = new MockTime();

        List<GroupMetadataValue.MemberMetadata> expectedMembers = new ArrayList<>();
        expectedMembers.add(
            new GroupMetadataValue.MemberMetadata()
                .setMemberId("member-1")
                .setClientId("client-1")
                .setClientHost("host-1")
                .setRebalanceTimeout(1000)
                .setSessionTimeout(1500)
                .setGroupInstanceId("group-instance-1")
                .setSubscription(new byte[]{0, 1})
                .setAssignment(new byte[]{1, 2})
        );

        expectedMembers.add(
            new GroupMetadataValue.MemberMetadata()
                .setMemberId("member-2")
                .setClientId("client-2")
                .setClientHost("host-2")
                .setRebalanceTimeout(1000)
                .setSessionTimeout(1500)
                .setGroupInstanceId("group-instance-2")
                .setSubscription(new byte[]{1, 2})
                .setAssignment(new byte[]{2, 3})
        );

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new GroupMetadataKey()
                    .setGroup("group-id"),
                (short) 2),
            new ApiMessageAndVersion(
                new GroupMetadataValue()
                    .setProtocol("range")
                    .setProtocolType("consumer")
                    .setLeader("member-1")
                    .setGeneration(1)
                    .setCurrentStateTimestamp(time.milliseconds())
                    .setMembers(expectedMembers),
                expectedGroupMetadataValueVersion));

        ClassicGroup group = new ClassicGroup(
            new LogContext(),
            "group-id",
            ClassicGroupState.PREPARING_REBALANCE,
            time,
            mock(GroupCoordinatorMetricsShard.class)
        );

        Map<String, byte[]> assignment = new HashMap<>();

        expectedMembers.forEach(member -> {
            JoinGroupRequestProtocolCollection protocols = new JoinGroupRequestProtocolCollection();
            protocols.add(new JoinGroupRequestProtocol()
                .setName("range")
                .setMetadata(member.subscription()));

            group.add(new ClassicGroupMember(
                member.memberId(),
                Optional.of(member.groupInstanceId()),
                member.clientId(),
                member.clientHost(),
                member.rebalanceTimeout(),
                member.sessionTimeout(),
                "consumer",
                protocols,
                ClassicGroupMember.EMPTY_ASSIGNMENT
            ));

            assignment.put(member.memberId(), member.assignment());
        });

        group.initNextGeneration();
        CoordinatorRecord groupMetadataRecord = CoordinatorRecordHelpers.newGroupMetadataRecord(
            group,
            assignment,
            metadataVersion
        );

        assertEquals(expectedRecord, groupMetadataRecord);
    }

    @Test
    public void testNewGroupMetadataTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new GroupMetadataKey()
                    .setGroup("group-id"),
                (short) 2),
            null);

        CoordinatorRecord groupMetadataRecord = CoordinatorRecordHelpers.newGroupMetadataTombstoneRecord("group-id");
        assertEquals(expectedRecord, groupMetadataRecord);
    }

    @Test
    public void testNewGroupMetadataRecordThrowsWhenNullSubscription() {
        Time time = new MockTime();

        List<GroupMetadataValue.MemberMetadata> expectedMembers = new ArrayList<>();
        expectedMembers.add(
            new GroupMetadataValue.MemberMetadata()
                .setMemberId("member-1")
                .setClientId("client-1")
                .setClientHost("host-1")
                .setRebalanceTimeout(1000)
                .setSessionTimeout(1500)
                .setGroupInstanceId("group-instance-1")
                .setSubscription(new byte[]{0, 1})
                .setAssignment(new byte[]{1, 2})
        );

        ClassicGroup group = new ClassicGroup(
            new LogContext(),
            "group-id",
            ClassicGroupState.PREPARING_REBALANCE,
            time,
            mock(GroupCoordinatorMetricsShard.class)
        );

        expectedMembers.forEach(member -> {
            JoinGroupRequestProtocolCollection protocols = new JoinGroupRequestProtocolCollection();
            protocols.add(new JoinGroupRequestProtocol()
                .setName("range")
                .setMetadata(null));

            group.add(new ClassicGroupMember(
                member.memberId(),
                Optional.of(member.groupInstanceId()),
                member.clientId(),
                member.clientHost(),
                member.rebalanceTimeout(),
                member.sessionTimeout(),
                "consumer",
                protocols,
                member.assignment()
            ));
        });

        assertThrows(IllegalStateException.class, () ->
            CoordinatorRecordHelpers.newGroupMetadataRecord(
                group,
                Collections.emptyMap(),
                MetadataVersion.IBP_3_5_IV2
            ));
    }

    @Test
    public void testNewGroupMetadataRecordThrowsWhenEmptyAssignment() {
        Time time = new MockTime();

        List<GroupMetadataValue.MemberMetadata> expectedMembers = new ArrayList<>();
        expectedMembers.add(
            new GroupMetadataValue.MemberMetadata()
                .setMemberId("member-1")
                .setClientId("client-1")
                .setClientHost("host-1")
                .setRebalanceTimeout(1000)
                .setSessionTimeout(1500)
                .setGroupInstanceId("group-instance-1")
                .setSubscription(new byte[]{0, 1})
                .setAssignment(null)
        );

        ClassicGroup group = new ClassicGroup(
            new LogContext(),
            "group-id",
            ClassicGroupState.PREPARING_REBALANCE,
            time,
            mock(GroupCoordinatorMetricsShard.class)
        );

        expectedMembers.forEach(member -> {
            JoinGroupRequestProtocolCollection protocols = new JoinGroupRequestProtocolCollection();
            protocols.add(new JoinGroupRequestProtocol()
                .setName("range")
                .setMetadata(member.subscription()));

            group.add(new ClassicGroupMember(
                member.memberId(),
                Optional.of(member.groupInstanceId()),
                member.clientId(),
                member.clientHost(),
                member.rebalanceTimeout(),
                member.sessionTimeout(),
                "consumer",
                protocols,
                member.assignment()
            ));
        });

        assertThrows(IllegalStateException.class, () ->
            CoordinatorRecordHelpers.newGroupMetadataRecord(
                group,
                Collections.emptyMap(),
                MetadataVersion.IBP_3_5_IV2
            ));
    }
      
    @ParameterizedTest
    @MethodSource("metadataToExpectedGroupMetadataValue")
    public void testEmptyGroupMetadataRecord(
        MetadataVersion metadataVersion,
        short expectedGroupMetadataValueVersion
    ) {
        Time time = new MockTime();

        List<GroupMetadataValue.MemberMetadata> expectedMembers = Collections.emptyList();

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new GroupMetadataKey()
                    .setGroup("group-id"),
                (short) 2),
            new ApiMessageAndVersion(
                new GroupMetadataValue()
                    .setProtocol(null)
                    .setProtocolType("")
                    .setLeader(null)
                    .setGeneration(0)
                    .setCurrentStateTimestamp(time.milliseconds())
                    .setMembers(expectedMembers),
                expectedGroupMetadataValueVersion));

        ClassicGroup group = new ClassicGroup(
            new LogContext(),
            "group-id",
            ClassicGroupState.PREPARING_REBALANCE,
            time,
            mock(GroupCoordinatorMetricsShard.class)
        );

        group.initNextGeneration();
        CoordinatorRecord groupMetadataRecord = CoordinatorRecordHelpers.newEmptyGroupMetadataRecord(
            group,
            metadataVersion
        );

        assertEquals(expectedRecord, groupMetadataRecord);
    }

    @ParameterizedTest
    @EnumSource(value = MetadataVersion.class)
    public void testNewOffsetCommitRecord(MetadataVersion metadataVersion) {
        OffsetCommitKey key = new OffsetCommitKey()
            .setGroup("group-id")
            .setTopic("foo")
            .setPartition(1);
        OffsetCommitValue value = new OffsetCommitValue()
            .setOffset(100L)
            .setLeaderEpoch(10)
            .setMetadata("metadata")
            .setCommitTimestamp(1234L)
            .setExpireTimestamp(-1L);

        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                key,
                (short) 1),
            new ApiMessageAndVersion(
                value,
                metadataVersion.offsetCommitValueVersion(false)
            )
        );

        assertEquals(expectedRecord, CoordinatorRecordHelpers.newOffsetCommitRecord(
            "group-id",
            "foo",
            1,
            new OffsetAndMetadata(
                100L,
                OptionalInt.of(10),
                "metadata",
                1234L,
                OptionalLong.empty()),
            metadataVersion
        ));

        value.setLeaderEpoch(-1);

        assertEquals(expectedRecord, CoordinatorRecordHelpers.newOffsetCommitRecord(
            "group-id",
            "foo",
            1,
            new OffsetAndMetadata(
                100L,
                OptionalInt.empty(),
                "metadata",
                1234L,
                OptionalLong.empty()),
            metadataVersion
        ));
    }

    @ParameterizedTest
    @EnumSource(value = MetadataVersion.class)
    public void testNewOffsetCommitRecordWithExpireTimestamp(MetadataVersion metadataVersion) {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new OffsetCommitKey()
                    .setGroup("group-id")
                    .setTopic("foo")
                    .setPartition(1),
                (short) 1),
            new ApiMessageAndVersion(
                new OffsetCommitValue()
                    .setOffset(100L)
                    .setLeaderEpoch(10)
                    .setMetadata("metadata")
                    .setCommitTimestamp(1234L)
                    .setExpireTimestamp(5678L),
                (short) 1 // When expire timestamp is set, it is always version 1.
            )
        );

        assertEquals(expectedRecord, CoordinatorRecordHelpers.newOffsetCommitRecord(
            "group-id",
            "foo",
            1,
            new OffsetAndMetadata(
                100L,
                OptionalInt.of(10),
                "metadata",
                1234L,
                OptionalLong.of(5678L)),
            metadataVersion
        ));
    }

    @Test
    public void testNewOffsetCommitTombstoneRecord() {
        CoordinatorRecord expectedRecord = new CoordinatorRecord(
            new ApiMessageAndVersion(
                new OffsetCommitKey()
                    .setGroup("group-id")
                    .setTopic("foo")
                    .setPartition(1),
                (short) 1),
            null);

        CoordinatorRecord record = CoordinatorRecordHelpers.newOffsetCommitTombstoneRecord("group-id", "foo", 1);
        assertEquals(expectedRecord, record);
    }

    /**
     * Creates a list of values to be added to the record and assigns partitions to racks for testing.
     *
     * @param numPartitions The number of partitions for the topic.
     *
     * For testing purposes, the following criteria are used:
     *      - Number of replicas for each partition: 2
     *      - Number of racks available to the cluster: 4
     */
    public static List<ConsumerGroupPartitionMetadataValue.PartitionMetadata> mkListOfPartitionRacks(int numPartitions) {
        List<ConsumerGroupPartitionMetadataValue.PartitionMetadata> partitionRacks = new ArrayList<>(numPartitions);
        for (int i = 0; i < numPartitions; i++) {
            List<String> racks = new ArrayList<>(Arrays.asList("rack" + i % 4, "rack" + (i + 1) % 4));
            partitionRacks.add(
                new ConsumerGroupPartitionMetadataValue.PartitionMetadata()
                    .setPartition(i)
                    .setRacks(racks)
            );
        }
        return partitionRacks;
    }

    /**
     * Creates a map of partitions to racks for testing.
     *
     * @param numPartitions The number of partitions for the topic.
     *
     * For testing purposes, the following criteria are used:
     *      - Number of replicas for each partition: 2
     *      - Number of racks available to the cluster: 4
     */
    public static Map<Integer, Set<String>> mkMapOfPartitionRacks(int numPartitions) {
        Map<Integer, Set<String>> partitionRacks = new HashMap<>(numPartitions);
        for (int i = 0; i < numPartitions; i++) {
            partitionRacks.put(i, new HashSet<>(Arrays.asList("rack" + i % 4, "rack" + (i + 1) % 4)));
        }
        return partitionRacks;
    }
}
