package com.github.litroenade.maidbridge.gametest;

import com.github.litroenade.maidbridge.Config;
import com.github.litroenade.maidbridge.MaidBridge;
import com.github.litroenade.maidbridge.trace.AiChainEvent;
import com.github.litroenade.maidbridge.trace.AiChainEventSink;
import com.github.litroenade.maidbridge.maid.turn.MaidAgentTurnRequest;
import com.github.litroenade.maidbridge.maid.turn.MaidExternalTurnGuard;
import com.github.litroenade.maidbridge.protocol.BridgeProtocol;
import com.github.litroenade.maidbridge.transport.BridgeTransport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(MaidBridge.MODID)
@PrefixGameTestTemplate(value = false)
@SuppressWarnings("unused")
public final class MaidBridgeProtocolGameTests {
    private MaidBridgeProtocolGameTests() {
    }

    @GameTest(template = "game_test", batch = "maidbridge_contract")
    public static void externalTurnRequestPublishWithoutStartedReleasesOnlyMatchingGuard(GameTestHelper helper) {
        var maidUuid = UUID.randomUUID().toString();
        var otherMaidUuid = UUID.randomUUID().toString();
        var turnId = "not-started-turn-" + UUID.randomUUID();
        var requestId = "not-started-request-" + UUID.randomUUID();
        var otherRequestId = "not-started-other-request-" + UUID.randomUUID();
        var transport = new BridgeTransport();
        if (!MaidExternalTurnGuard.tryBeginExternalTurn(maidUuid, turnId, requestId, "hello", Map.of()).accepted()) {
            helper.fail("预置外部轮次应当被接受");
            return;
        }
        if (!MaidExternalTurnGuard.tryBeginExternalTurn(otherMaidUuid, turnId, otherRequestId, "keep me", Map.of()).accepted()) {
            helper.fail("预置另一只女仆的外部轮次应当被接受");
            return;
        }
        try {
            transport.publish(new AiChainEvent(
                    BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST,
                    1,
                    externalTurnPayload(maidUuid, turnId, "hello")
            ));

            assertTurnReleased(helper, maidUuid, turnId, "传输层未启动时发布");
            assertTurnPending(helper, otherMaidUuid, turnId, otherRequestId);
        } finally {
            releaseTurnIfPresent(maidUuid, turnId);
            releaseTurnIfPresent(otherMaidUuid, turnId);
        }

        helper.succeed();
    }

    @GameTest(template = "game_test", batch = "maidbridge_contract")
    public static void externalTurnPublishWhenBridgeServerDisabledReleasesGuardByTurnIdentity(GameTestHelper helper) {
        var previousBridgeServerEnabled = Config.bridgeServerEnabled;
        var previousMessageBridge = Config.enableMaidMessageBridge;
        var previousExternalTurns = Config.enableExternalMaidAgentTurns;
        var maidUuid = UUID.randomUUID().toString();
        var otherMaidUuid = UUID.randomUUID().toString();
        var turnId = "queued-drop-turn-" + UUID.randomUUID();
        var requestId = "queued-drop-request-" + UUID.randomUUID();
        var otherRequestId = "queued-drop-other-request-" + UUID.randomUUID();
        var transport = new BridgeTransport();
        if (!MaidExternalTurnGuard.tryBeginExternalTurn(maidUuid, turnId, requestId, "first", Map.of()).accepted()) {
            helper.fail("预置外部轮次应当被接受");
            return;
        }
        if (!MaidExternalTurnGuard.tryBeginExternalTurn(otherMaidUuid, turnId, otherRequestId, "second", Map.of()).accepted()) {
            helper.fail("预置另一只女仆的外部轮次应当被接受");
            return;
        }
        try {
            Config.bridgeServerEnabled = false;
            Config.enableMaidMessageBridge = true;
            Config.enableExternalMaidAgentTurns = true;
            transport.configure(1);
            transport.start(null);

            transport.publish(new AiChainEvent(
                    BridgeProtocol.TYPE_MAID_AGENT_TURN_REQUEST,
                    1,
                    externalTurnPayload(maidUuid, turnId, "first")
            ));
            assertTurnReleased(helper, maidUuid, turnId, "桥接服务关闭");
            assertTurnPending(helper, otherMaidUuid, turnId, otherRequestId);
        } finally {
            transport.stop();
            Config.bridgeServerEnabled = previousBridgeServerEnabled;
            Config.enableMaidMessageBridge = previousMessageBridge;
            Config.enableExternalMaidAgentTurns = previousExternalTurns;
            releaseTurnIfPresent(maidUuid, turnId);
            releaseTurnIfPresent(otherMaidUuid, turnId);
        }

        helper.succeed();
    }

    @GameTest(template = "game_test", batch = "maidbridge_contract")
    public static void duplicateExternalTurnReportsBusyInsteadOfStartingNativeFallback(GameTestHelper helper) {
        var previousBridgeServerEnabled = Config.bridgeServerEnabled;
        var previousMessageBridge = Config.enableMaidMessageBridge;
        var previousExternalTurns = Config.enableExternalMaidAgentTurns;
        var maidUuid = UUID.randomUUID();
        try {
            Config.bridgeServerEnabled = true;
            Config.enableMaidMessageBridge = true;
            Config.enableExternalMaidAgentTurns = true;
            AiChainEventSink.setConsumer(null);
            AiChainEventSink.clear();

            var manager = new FakeChatManager(new FakeMaid(maidUuid));
            var first = MaidAgentTurnRequest.emit(manager, "first", new FakeClientInfo(UUID.randomUUID()), new FakeSender(UUID.randomUUID()));
            var second = MaidAgentTurnRequest.emit(manager, "second", new FakeClientInfo(UUID.randomUUID()), new FakeSender(UUID.randomUUID()));

            if (first.status() != MaidAgentTurnRequest.EmitStatus.EMITTED) {
                helper.fail("第一次外部轮次必须被接受：" + first);
                return;
            }
            if (second.status() != MaidAgentTurnRequest.EmitStatus.BUSY) {
                helper.fail("重复外部轮次必须报告为忙碌，避免回落到原生聊天：" + second);
                return;
            }
            if (AiChainEventSink.snapshot().stream().noneMatch(event ->
                    "maid.agent.turn.rejected".equals(event.type())
                            && "maid_external_turn_pending".equals(event.payload().get("reason"))
                            && maidUuid.toString().equals(((Map<?, ?>) event.payload().get("maid")).get("uuid")))) {
                helper.fail("重复外部轮次必须发出可诊断的拒绝事件");
                return;
            }
        } finally {
            for (var turn : MaidExternalTurnGuard.snapshotExternalTurns()) {
                if (maidUuid.toString().equals(turn.maidUuid())) {
                    MaidExternalTurnGuard.completeExternalTurn(turn.maidUuid(), turn.turnId());
                }
            }
            AiChainEventSink.clear();
            Config.bridgeServerEnabled = previousBridgeServerEnabled;
            Config.enableMaidMessageBridge = previousMessageBridge;
            Config.enableExternalMaidAgentTurns = previousExternalTurns;
        }

        helper.succeed();
    }

    private static Map<String, Object> externalTurnPayload(String maidUuid, String turnId, String text) {
        return Map.of(
                "turn_id", turnId,
                "maid", Map.of("uuid", maidUuid, "name", "GameTest 女仆"),
                "speaker", Map.of("name", "GameTest 发言者"),
                "message", Map.of("text", text),
                "state", Map.of("alive", true),
                "actions", List.of(),
                "tools", List.of()
        );
    }

    private static void assertTurnReleased(GameTestHelper helper, String maidUuid, String turnId, String reason) {
        var activeTurn = MaidExternalTurnGuard.findExternalTurn(maidUuid, turnId);
        if (activeTurn != null) {
            helper.fail("期望待处理外部轮次在 " + reason + " 后释放：女仆=" + maidUuid + " turn_id=" + turnId + " request_id=" + activeTurn.requestId());
        }
    }

    private static void assertTurnPending(GameTestHelper helper, String maidUuid, String turnId, String expectedRequestId) {
        String reason = "另一只女仆上的相同 turn_id 不应被释放";
        var activeTurn = MaidExternalTurnGuard.findExternalTurn(maidUuid, turnId);
        if (activeTurn == null) {
            helper.fail("期望待处理外部轮次在 " + reason + " 后仍然保留：女仆=" + maidUuid + " turn_id=" + turnId);
            return;
        }
        if (!expectedRequestId.equals(activeTurn.requestId())) {
            helper.fail("待处理外部轮次的 request_id 在 " + reason + " 后发生变化：期望=" + expectedRequestId + " 实际=" + activeTurn.requestId());
        }
    }

    private static void releaseTurnIfPresent(String maidUuid, String turnId) {
        MaidExternalTurnGuard.completeExternalTurn(maidUuid, turnId);
    }

    private record FakeChatManager(FakeMaid maid) {
        public FakeMaid getMaid() {
            return maid;
        }
    }

    private record FakeMaid(UUID uuid) {
        public UUID getUUID() {
            return uuid;
        }

        public FakeName getName() {
            return new FakeName(uuid);
        }

        public boolean isMaidInSittingPose() {
            return (uuid.getLeastSignificantBits() & 1L) != 0L;
        }

        public boolean isHomeModeEnable() {
            return (uuid.getLeastSignificantBits() & 2L) != 0L;
        }

        public String getSchedule() {
            return (uuid.getLeastSignificantBits() & 4L) == 0L ? "ALL" : "DAY";
        }

        public String getScheduleDetail() {
            return (uuid.getLeastSignificantBits() & 8L) == 0L ? "IDLE" : "WORK";
        }
    }

    private record FakeName(UUID uuid) {
        public String getString() {
            return "GameTest-" + uuid.toString().substring(0, 8);
        }
    }

    private record FakeClientInfo(UUID uuid) {
        public String language() {
            return (uuid.getLeastSignificantBits() & 1L) == 0L ? "zh_cn" : "en_us";
        }

        public String name() {
            return "客户端-" + uuid.toString().substring(0, 8);
        }

        public List<String> description() {
            return List.of("GameTest", uuid.toString());
        }
    }

    private record FakeSender(UUID uuid) {
        public UUID getUUID() {
            return uuid;
        }

        public FakeName getName() {
            return new FakeName(uuid);
        }
    }
}
