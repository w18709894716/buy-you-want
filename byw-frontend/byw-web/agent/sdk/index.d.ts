/**
 * @aam/sdk — 公共入口，导出所有 API 和类型。
 */
export { AgentClient } from "./client";
export type { AgentClientOptions, RunResult, ClientEmittedEvent } from "./client";
export { AgentWidget } from "./widget";
export type { AgentWidgetOptions } from "./widget";
export { CapabilityRegistry, defaultRegistry, registerTool } from "./capability";
export type { ToolDefinition, ToolHandler } from "./capability";
export { snapshotPage, generateSelector } from "./dom-analyzer";
export type { PageElement } from "./dom-analyzer";
export { executeAction, BUILTIN_DOM_TOOLS } from "./executor";
export type { ActionResult } from "./executor";
export { PROTOCOL_VERSION, CLIENT_EVENT, SERVER_EVENT, } from "./types";
export type { EventEnvelope, JSONSchema, RiskLevel, CapabilitySpec, ClientEventType, ServerEventType, RunStatus, SessionStartPayload, TaskSubmitPayload, ActionResultPayload, ConfirmResponsePayload, TaskCancelPayload, SessionReadyPayload, RunStartedPayload, TextDeltaPayload, ActionRequestPayload, ConfirmRequestPayload, RunFinishedPayload, RunErrorPayload, StateDeltaPayload, ClientEvent, ServerEvent, SessionStartEvent, TaskSubmitEvent, ActionResultEvent, ConfirmResponseEvent, TaskCancelEvent, SessionReadyEvent, RunStartedEvent, TextDeltaEvent, ActionRequestEvent, ConfirmRequestEvent, RunFinishedEvent, RunErrorEvent, StateDeltaEvent, } from "./types";
