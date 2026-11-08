"""Read-only upstream API and management drift check using the installed GitHub CLI. Parse source as AST; never import/execute it.

Examples:
  python -B scripts/check_hermes_contract.py --ref latest --output E:/Temp/hermes-stable.json
  python -B scripts/check_hermes_contract.py --ref main --output E:/Temp/hermes-main.json
Exit 2 means a used route/feature or a tracked constraint changed. A zero exit is NOT
an interoperability/security certification; inspect source/feature additions and run live contracts. The default baseline tracks the current implemented client; use --baseline for historical evidence.
"""
from __future__ import annotations
import argparse
import ast
import copy
import concurrent.futures
import json
import hashlib
from pathlib import Path
import re
import sys
import base64
import subprocess
import math
import threading
import time
import os
import tempfile

REPO = 'NousResearch/hermes-agent'
SOURCES = {
    'api': 'gateway/platforms/api_server.py',
    'runs': 'gateway/platforms/api_server_runs.py',
    'rooms': 'gateway/platforms/api_server_room_grants.py',
}
EXTRA_SOURCES = {
    'authority': 'gateway/platforms/api_server_authority_runs.py',
    'auth': 'hermes_cli/dashboard_auth/routes.py',
    'profiles': 'hermes_cli/web_routers/profiles.py',
    'mcp': 'hermes_cli/web_routers/mcp.py',
    'mcp_helpers': 'hermes_cli/web_server_mcp.py',
    'mcp_catalog': 'hermes_cli/mcp_catalog.py',
    'mcp_oauth': 'tools/mcp_dashboard_oauth.py',
    'cron': 'hermes_cli/web_routers/cron.py',
    'cron_helpers': 'hermes_cli/web_server_cron.py',
    'cron_jobs': 'cron/jobs.py',
    'cron_time': 'cron/jobs_time.py',
    'cron_invariants': 'cron/jobs_invariants.py',
    'cron_scheduler': 'cron/scheduler.py',
    'gateway_models': 'tui_gateway/contracts/tools_commands.py',
    'gateway_rpc': 'tui_gateway/methods_tools.py',
    'gateway_scope': 'tui_gateway/model_switch.py',
    'gateway_runtime': 'tui_gateway/server.py',
    'gateway_ws': 'tui_gateway/ws.py',
    'chat_ws': 'hermes_cli/web_routers/chat_ws.py',
    'chat_auth': 'hermes_cli/web_server_chat.py',
    'ws_boundary': 'hermes_cli/web_server_boundary.py',
    'ws_tickets': 'hermes_cli/dashboard_auth/ws_tickets.py',
    'cron_cancel': 'cron/cancellation.py',
    'cron_tool': 'tools/cronjob_tools.py',
    'cron_format': 'tools/cronjob_job_args.py',
    'skills': 'hermes_cli/web_routers/skills.py',
    'skills_config': 'hermes_cli/skills_config.py',
    'skill_usage': 'tools/skill_usage.py',
    'skill_manager': 'tools/skill_manager_tool.py',
    'skill_catalog': 'tools/skills_tool.py',
    'skill_utils': 'agent/skill_utils.py',
    'web_bootstrap': 'hermes_cli/web_server.py',
    'models': 'hermes_cli/web_models.py',
    'profile_helpers': 'hermes_cli/web_server_profiles.py',
    'hub_cli': 'hermes_cli/skills_hub.py',
    'hub_search': 'tools/skills_hub_search.py',
    'hub_claw': 'tools/skills_hub_clawhub.py',
    'hub_official': 'tools/skills_hub_official.py',
    'hub_guard': 'tools/skills_guard.py',
    'hub_tier1': 'tools/skillevaluator_scan.py',
    'hub_install': 'tools/skills_hub_install.py',
    'hub_models': 'tools/skills_hub_models.py',
    'web_common': 'hermes_cli/web_routers/_common.py',
    'web_deps': 'hermes_cli/web_deps.py',
    'host_actions': 'hermes_cli/web_server_gateway.py',
    'bundled_ops': 'tools/skills_sync_bundled_ops.py',
    'gateway_plugin_models': 'tui_gateway/contracts/tools_mcp_plugins.py',
    'gateway_common': 'tui_gateway/contracts/common.py',
    'plugin_core': 'hermes_cli/plugins_cmd.py',
    'plugin_servers': 'tui_gateway/methods_tools_plugin_servers.py',
    'gateway_lookup': 'tui_gateway/methods_plugin_catalog.py',
    'gateway_method_ctx': 'tui_gateway/method_ctx.py',
    'plugin_inspect': 'hermes_cli/plugins_cmd_inspect.py',
    'plugin_presence': 'hermes_cli/plugin_catalog_presence.py',
    'plugin_catalog_gate': 'hermes_cli/plugins_cmd_catalog.py',
    'plugin_catalog_loader': 'hermes_cli/plugin_catalog.py',
    'plugin_git': 'hermes_cli/plugins_cmd_git.py',
    'plugin_install': 'hermes_cli/plugins_cmd_install.py',
    'plugin_update': 'hermes_cli/plugins_cmd_update.py',
    'plugin_settings': 'hermes_cli/plugins_settings.py',
    'env_routes': 'hermes_cli/web_routers/config_env.py',
    'custom_provider_config': 'hermes_cli/config_providers.py',
    'probe_metadata': 'agent/model_metadata.py',
    'probe_route_resolver': 'hermes_cli/runtime_provider.py',
    'env_lifecycle': 'hermes_cli/credential_lifecycle.py',
    'env_config': 'hermes_cli/config.py',
    'env_external': 'hermes_cli/auth_external_process.py',
    'model_routes': 'hermes_cli/web_routers/models.py',
    'moa_config': 'hermes_cli/moa_config.py',
    'oauth_routes': 'hermes_cli/web_routers/oauth.py',
    'oauth_helpers': 'hermes_cli/web_server_oauth.py',
    'provider_auth': 'hermes_cli/auth.py',
    'pool_routes': 'hermes_cli/web_routers/ops.py',
    'fallback_config': 'hermes_cli/fallback_config.py',
    'fallback_wire': 'agent/chat_completion_helpers.py',
    'fallback_binding': 'agent/route_binding.py',
    'config_backend': 'hermes_cli/config_backend.py',
    'config_worker_save': 'hermes_cli/config_worker_save.py',
    'provider_identity': 'hermes_cli/providers.py',
    'credential_pool': 'agent/credential_pool.py',
    'pool_admin': 'agent/credential_pool_admin.py',
    'pool_sources': 'agent/credential_sources.py',
    'pool_persistence': 'agent/credential_persistence.py',
    'auth_middleware': 'hermes_cli/dashboard_auth/middleware.py',
    'model_config': 'hermes_cli/web_server_config.py',
    'model_inventory': 'hermes_cli/inventory.py',
    'model_catalog': 'hermes_cli/model_switch_providers.py',
    'model_auxiliary': 'agent/auxiliary_task_config.py',
    'model_aux_runtime': 'agent/auxiliary_client.py',
    'model_custom_runtime': 'hermes_cli/runtime_provider_custom.py',
    'model_reasoning': 'hermes_constants.py',
    'model_defaults': 'hermes_cli/config_defaults.py',
    'model_aux_defaults': 'hermes_cli/config_defaults_auxiliary.py',

    'model_switch': 'hermes_cli/model_switch.py',
    'model_guards': 'hermes_cli/model_selection_guards.py',
    'model_cost': 'hermes_cli/model_cost_guard.py',
    'model_data': 'hermes_cli/model_data_policy_guard.py',
    'model_route_identity': 'hermes_cli/route_identity.py',



    'plugin_state': 'hermes_cli/plugins_state.py',
    'mcp_discovery': 'tools/mcp_tool_discovery.py',

    'plugin_guard': 'tools/plugin_guard.py',

    'plugin_remove': 'hermes_cli/plugins_cmd_remove.py',
    'plugin_oauth': 'hermes_cli/plugin_oauth.py',
    'plugin_grant_roots': 'hermes_cli/plugin_provider_requests.py',

    'plugin_admission': 'hermes_cli/plugins_admission.py',
    'plugin_legacy': 'hermes_cli/left_core_migration.py',
    'pm_client': 'pm/client.py',

    'plugin_activation': 'hermes_cli/plugins_activation.py',
    'plugin_transaction': 'hermes_cli/plugins_transaction.py',





}
# Direct auth/import dependencies reviewed at pinned sources; upstream is parsed only.
RUNTIME_GUARD_CONTRACT_SOURCES = {'web_signin': 'hermes_cli/webapp_sign_in.py', 'plugin_isolation': 'hermes_cli/plugin_isolation.py', 'signin_entry': 'hermes_cli/dashboard_auth/__init__.py', 'signin_registry': 'hermes_cli/dashboard_auth/registry.py', 'session_token_provider': 'plugins/dashboard_auth/session_token/__init__.py', 'session_token_loader': 'hermes_cli/web_server_session_token.py', 'auth_request_utils': 'hermes_cli/dashboard_auth/request_utils.py', 'signin_shared': 'plugins/dashboard_auth/_shared.py', 'signin_registry_engine': 'agent/provider_registry.py', 'gateway_server_requests': 'tui_gateway/server_requests.py'}
EXTRA_SOURCES.update(RUNTIME_GUARD_CONTRACT_SOURCES)

# Private-grant producer/peer boundary and saved-gateway callback dependencies.
AUTH_BOUNDARY_CONTRACT_SOURCES = {'auth_native_http': 'hermes_cli/dashboard_auth/native_http.py', 'auth_native_bootstrap': 'gateway/runtime_bootstrap.py', 'auth_native_run_api': 'gateway/run_api.py', 'saved_gateway_oauth': 'hermes_cli/gateway_connections_oauth.py', 'saved_gateway_routes': 'hermes_cli/web_routers/gateway_connections_reach.py', 'saved_gateway_store': 'hermes_cli/gateway_connections.py', 'saved_gateway_normalize': 'hermes_cli/gateway_connections_normalize.py'}
EXTRA_SOURCES.update(AUTH_BOUNDARY_CONTRACT_SOURCES)
CANONICAL_NEGOTIATION_CONTRACT_SOURCES = {
    'canonical_controls': 'gateway/session_controls.py',
    'canonical_wire': 'tui_gateway/contracts/canonical.py',
}
EXTRA_SOURCES.update(CANONICAL_NEGOTIATION_CONTRACT_SOURCES)
CANONICAL_ATTACHMENT_CONTRACT_SOURCES = {'canonical_sessions': 'tui_gateway/contracts/canonical_sessions.py', 'canonical_listing': 'gateway/session_listing.py', 'canonical_migration': 'gateway/session_local_migration.py', 'canonical_viewers': 'gateway/session_viewer_close.py', 'canonical_authority': 'gateway/session_authority.py', 'canonical_owner': 'gateway/owner_identity.py', 'canonical_snapshot': 'gateway/session_local_inventory.py', 'canonical_policy': 'gateway/session_policy_refresh.py', 'canonical_live_listing': 'gateway/session_live_listing.py'}
EXTRA_SOURCES.update(CANONICAL_ATTACHMENT_CONTRACT_SOURCES)
MAIN_AUTHORITY_CONTRACT_SOURCES = {'canonical_stored_reads': 'gateway/session_stored_reads.py', 'canonical_picker': 'gateway/session_picker_fields.py', 'canonical_plugin_rpc': 'gateway/session_plugin_rpc.py', 'canonical_recovery': 'gateway/session_local_recovery.py', 'canonical_transcript_rows': 'gateway/session_transcript_rows.py', 'canonical_local_receipts': 'hermes_state_local.py', 'canonical_local_lineage': 'hermes_state_local_lineage.py', 'canonical_policy_state': 'gateway/session_policy.py'}
EXTRA_SOURCES.update(MAIN_AUTHORITY_CONTRACT_SOURCES)




PLUGIN_UPDATE_CONTRACT_SOURCES = {'plugin_auto_update': 'hermes_cli/plugins_auto_update.py'}
EXTRA_SOURCES.update(PLUGIN_UPDATE_CONTRACT_SOURCES)
# Official complete trees and Contents 404s prove this dependency is absent at ONLY these pins.
# A missing dependency at any other commit is an incomplete inspection, never empty success.
REVIEWED_SOURCE_ABSENCES = {
    'canonical_transcript_rows': ('gateway/session_transcript_rows.py', frozenset(('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'))),
    'canonical_plugin_rpc': ('gateway/session_plugin_rpc.py', frozenset(('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'))),
    'canonical_picker': ('gateway/session_picker_fields.py', frozenset(('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'))),
    'canonical_stored_reads': ('gateway/session_stored_reads.py', frozenset(('f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', '2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19'))),
    'canonical_owner': ('gateway/owner_identity.py', frozenset(('bf0203a794a07aac3ff764972ab9087baa4e4f4e',))),
    'saved_gateway_oauth': ('hermes_cli/gateway_connections_oauth.py', frozenset(('2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', 'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab'))),
    'saved_gateway_routes': ('hermes_cli/web_routers/gateway_connections_reach.py', frozenset(('2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', 'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab'))),
    'saved_gateway_store': ('hermes_cli/gateway_connections.py', frozenset(('2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', 'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab'))),
    'saved_gateway_normalize': ('hermes_cli/gateway_connections_normalize.py', frozenset(('2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19', 'b200f5bc60e31588ba2cb60e288029c4a54ba5fa', 'bf0203a794a07aac3ff764972ab9087baa4e4f4e', 'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab'))),
    'plugin_auto_update': ('hermes_cli/plugins_auto_update.py', frozenset({
        'f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab',
        'b200f5bc60e31588ba2cb60e288029c4a54ba5fa',
    })),
}

def reviewed_source_absence(name: str, path: str, commit: str) -> bool:
    entry = REVIEWED_SOURCE_ABSENCES.get(name)
    return bool(entry and EXTRA_SOURCES.get(name) == path == entry[0] and commit in entry[1])

KANBAN_CONTRACT_SOURCES = {'kanban_api':'plugins/kanban/dashboard/plugin_api.py','kanban_db':'hermes_cli/kanban_db.py','kanban_boards':'hermes_cli/kanban_db_boards.py','kanban_connect':'hermes_cli/kanban_db_connect.py','kanban_workflow':'hermes_cli/kanban_workflow.py','kanban_plugin_profiles':'hermes_cli/web_server_plugin_profiles.py','kanban_plugin_mount':'hermes_cli/web_server_dashboard.py','kanban_read_coalescing':'hermes_cli/web_read_coalescing.py'}
KANBAN_CONTRACT_SOURCES.update({'kanban_scope_runtime':'hermes_cli/web_server.py','kanban_roots':'hermes_constants.py'})
KANBAN_CONTRACT_SOURCES['kanban_graph']='hermes_cli/kanban_db_graph.py'
KANBAN_CONTRACT_SOURCES['kanban_profiles']='hermes_cli/profiles.py'
EXTRA_SOURCES.update(KANBAN_CONTRACT_SOURCES)
MANAGEMENT_ROUTE_PREFIXES = {'kanban_api':'/api/plugins/kanban'}

PROJECT_CONTRACT_SOURCES = {'project_contract':'tui_gateway/contracts/projects_pets.py','project_methods':'tui_gateway/methods_projects.py','project_db':'hermes_cli/projects_db.py'}
EXTRA_SOURCES.update(PROJECT_CONTRACT_SOURCES)

CHANNEL_CONTRACT_SOURCES = {'channels': 'hermes_cli/web_routers/messaging.py', 'channel_catalog': 'hermes_cli/web_server_messaging.py', 'channel_registry': 'gateway/platform_registry.py', 'channel_profiles': 'hermes_cli/profile_channels.py', 'channel_status': 'gateway/status.py', 'channel_served': 'gateway/status_served.py', 'channel_pairing': 'gateway/pairing.py', 'channel_config_env': 'gateway/config_env.py', 'channel_multiplex': 'hermes_cli/gateway_multiplex_mode.py', 'channel_env_loader': 'hermes_cli/env_loader.py', 'channel_gate': 'gateway/platforms/_shared.py', 'channel_hidden': 'hermes_cli/setup_hidden_env.py', 'channel_config': 'gateway/config.py'}
CHANNEL_CONTRACT_SOURCES.update({'channel_relay_config':'gateway/relay/__init__.py','channel_relay_links':'gateway/relay/link.py'})
CHANNEL_CONTRACT_SOURCES.update({'relay_routes': 'hermes_cli/web_routers/messaging_relay.py', 'relay_control': 'gateway/control_socket.py', 'relay_auth': 'gateway/relay/auth.py', 'relay_media': 'gateway/relay/media.py', 'relay_diagnostics': 'gateway/relay/http_diagnostics.py', 'relay_portal': 'hermes_cli/auth_nous.py'})
EXTRA_SOURCES.update(CHANNEL_CONTRACT_SOURCES)

MANAGEMENT_SOURCES = {'auth', 'profiles', 'mcp', 'cron', 'skills', 'chat_ws', 'env_routes', 'model_routes', 'oauth_routes', 'pool_routes'}
MANAGEMENT_SOURCES.update(("channels","relay_routes","kanban_api"))
MANAGEMENT_SOURCES.add("saved_gateway_routes")

def ast_signature(value):
    # ast.dump formatting/default fields differ across supported Python releases.
    if isinstance(value, ast.AST):
        return {'node': type(value).__name__, **{name: ast_signature(item) for name,item in ast.iter_fields(value) if item is not None and item != []}}
    if isinstance(value, list):
        return [ast_signature(item) for item in value]
    if isinstance(value, bytes):
        return {'bytes': value.hex()}
    if value is Ellipsis:
        return {'ellipsis': True}
    if isinstance(value, complex):
        return {'complex': [value.real, value.imag]}
    return value

def fingerprint(node):
    return hashlib.sha256(json.dumps(ast_signature(node),sort_keys=True,separators=(',',':'),ensure_ascii=True).encode()).hexdigest()

def definitions(tree: ast.AST, source: str) -> tuple[dict, dict]:
    handlers, fields = {}, {}
    if source in CANONICAL_ATTACHMENT_CONTRACT_SOURCES and tree.body:
        handlers[f'{source}:consumed_module']=fingerprint(tree)
    def visit(nodes, prefix=''):
        for node in nodes:
            if isinstance(node, ast.ClassDef):
                if source=='canonical_wire' and not prefix and node.name in {'RuntimeDescribeParams','RuntimeDescribeResult','SessionCreateDescriptor'}:handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                project_classes={'project_contract':{'ProjectFolder','ProjectInfo','ProjectsPayload','ProjectResult','ProjectIdParams','OptionalProjectResult','ProjectsCreateParams','ProjectsUpdateParams','ProjectsAddFolderParams','ProjectFolderParams','ProjectsArchiveParams','ProjectsSetActiveParams','ActiveIdResult'},'project_db':{'Project','ProjectFolder'},'project_methods':{'_NoProject'}}
                if not prefix and node.name in project_classes.get(source,set()):handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                kanban_classes={'kanban_api':{'CreateBoardBody','RenameBoardBody','CreateTaskBody','UpdateTaskBody','CommentBody','LinkBody'},'kanban_db':{'Task','Run','Comment','Event','Attachment'},'kanban_workflow':{'Column','Workflow'}}
                if not prefix and node.name in kanban_classes.get(source,set()):handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                channel_classes={'channel_registry':{'PlatformEntry'},'channel_config':{'Platform','HomeChannel'},'channel_status':{'GatewayLiveness'}}
                if not prefix and node.name in channel_classes.get(source,set()):handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                if (source=='gateway_models' and node.name=='CronAction') or (source=='gateway_plugin_models' and node.name in ('PluginsAction','PluginCatalogLookupStatus','PluginRestartTarget','PluginSettingFieldType')):handlers[f'{source}:enum:{node.name}']=fingerprint(node)
                if source=='gateway_plugin_models' and node.name=='PluginsManageParams':handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                if source=='plugin_auto_update' and node.name=='HeldWithRef':handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                if source=='models' and node.name in ('EnvVarUpdate','EnvVarDelete','ModelAssignment','MoaModelSlot','_MoaReferenceControls','MoaPresetPayload','MoaConfigPayload','CredentialPoolAdd','ConfigUpdate','CustomEndpointUpdate','CustomEndpointModelDetail','MessagingPlatformUpdate'):handlers[f'{source}:schema:{node.name}']=fingerprint(node)
                visit(node.body, prefix+node.name+'.')
                for item in node.body:
                    if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
                        fields[f'{source}:{prefix}{node.name}.{item.target.id}'] = fingerprint(item)
            elif source=='canonical_wire' and not prefix and isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and isinstance(node.value.func,ast.Name) and node.value.func.id=='canonical_method' and node.value.args and isinstance(node.value.args[0],ast.Constant) and node.value.args[0].value=='runtime.describe':
                selected=copy.deepcopy(node);selected.value.keywords=[keyword for keyword in selected.value.keywords if keyword.arg!='doc']
                handlers['canonical_wire:registration:runtime.describe']=fingerprint(selected)
            elif not prefix and source=='provider_auth' and isinstance(node,ast.Try):
                platforms={alias.name for child in ast.walk(node) if isinstance(child,ast.Import) for alias in child.names} & {'fcntl','msvcrt'}
                for platform in sorted(platforms):handlers[f'{source}:lock_platform:{platform}']=fingerprint(node)
            elif not prefix and source=='gateway_runtime' and isinstance(node,ast.ImportFrom) and any(alias.name=='methods_projects' for alias in node.names):
                isolated=ast.ImportFrom(module=node.module,names=[alias for alias in node.names if alias.name=='methods_projects'],level=node.level)
                handlers['gateway_runtime:projects_registration_import']=fingerprint(isolated)
            elif not prefix and source=='gateway_runtime' and isinstance(node,ast.For) and isinstance(node.iter,(ast.Tuple,ast.List)) and any(isinstance(item,ast.Name) and item.id=='_methods_projects' for item in node.iter.elts):
                selected=copy.deepcopy(node)
                selected.iter.elts=[item for item in selected.iter.elts if isinstance(item,ast.Name) and item.id=='_methods_projects']
                handlers['gateway_runtime:projects_registration_loop']=fingerprint(selected)
            elif source in (RUNTIME_GUARD_CONTRACT_SOURCES.keys() | AUTH_BOUNDARY_CONTRACT_SOURCES.keys() | CANONICAL_NEGOTIATION_CONTRACT_SOURCES.keys() | CANONICAL_ATTACHMENT_CONTRACT_SOURCES.keys() | MAIN_AUTHORITY_CONTRACT_SOURCES.keys()) and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name):handlers[f'{source}:binding:{prefix}{target.id}']=fingerprint(node)
            elif source in (RUNTIME_GUARD_CONTRACT_SOURCES.keys() | AUTH_BOUNDARY_CONTRACT_SOURCES.keys() | CANONICAL_NEGOTIATION_CONTRACT_SOURCES.keys() | CANONICAL_ATTACHMENT_CONTRACT_SOURCES.keys() | MAIN_AUTHORITY_CONTRACT_SOURCES.keys()) and isinstance(node,(ast.Import,ast.ImportFrom)):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    isolated=ast.ImportFrom(module=node.module,names=[alias],level=node.level) if isinstance(node,ast.ImportFrom) else ast.Import(names=[alias])
                    handlers[f'{source}:import:{prefix}{binding}']=fingerprint(isolated)
            elif not prefix and source in (PROJECT_CONTRACT_SOURCES.keys() | KANBAN_CONTRACT_SOURCES.keys() | PLUGIN_UPDATE_CONTRACT_SOURCES.keys()) and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    for binding in ast.walk(target):
                        if isinstance(binding,ast.Name):handlers[f'{source}:binding:{binding.id}']=fingerprint(node)
            elif not prefix and source in (PROJECT_CONTRACT_SOURCES.keys() | KANBAN_CONTRACT_SOURCES.keys() | PLUGIN_UPDATE_CONTRACT_SOURCES.keys()) and isinstance(node,(ast.Import,ast.ImportFrom)):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    isolated=ast.ImportFrom(module=node.module,names=[alias],level=node.level) if isinstance(node,ast.ImportFrom) else ast.Import(names=[alias])
                    handlers[f'{source}:import:{binding}']=fingerprint(isolated)
            elif not prefix and source in PROJECT_CONTRACT_SOURCES and isinstance(node,ast.Expr) and isinstance(node.value,ast.Call) and isinstance(node.value.func,ast.Name) and node.value.args and isinstance(node.value.args[0],ast.Constant) and isinstance(node.value.args[0].value,str):
                call=node.value;name=call.args[0].value
                if source=='project_contract' and call.func.id=='method' and name.startswith('projects.'):
                    normalized=copy.deepcopy(node);normalized.value.keywords=[keyword for keyword in normalized.value.keywords if keyword.arg!='doc']
                    handlers[f'{source}:method:{name}']=fingerprint(normalized)
                elif source=='project_methods' and call.func.id=='_register_project_mutator':
                    handlers[f'{source}:mutator:{name}']=fingerprint(node)
            elif not prefix and source in CHANNEL_CONTRACT_SOURCES and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name):handlers[f'{source}:binding:{target.id}']=fingerprint(node)
            elif not prefix and source in CHANNEL_CONTRACT_SOURCES and isinstance(node,(ast.Import,ast.ImportFrom)):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    isolated=ast.ImportFrom(module=node.module,names=[alias],level=node.level) if isinstance(node,ast.ImportFrom) else ast.Import(names=[alias])
                    handlers[f'{source}:import:{binding}']=fingerprint(isolated)
            elif not prefix and isinstance(node,ast.Assign) and source in ('ws_boundary','ws_tickets','cron_tool','cron_format','hub_guard','hub_tier1','bundled_ops','gateway_rpc','plugin_inspect','plugin_presence','plugin_remove','plugin_catalog_gate'):
                selected={'ws_boundary':{'_GATEWAY_WS_PROTOCOL','_GATEWAY_WS_TICKET_PROTOCOL_PREFIX'},'ws_tickets':{'TTL_SECONDS'},'cron_tool':{'_JOB_ACTIONS','_JOBLESS_ACTIONS'},'cron_format':{'_FORMAT_JOB_OPTIONAL_KEYS'},'hub_guard':{'INSTALL_POLICY','VERDICT_INDEX'},'hub_tier1':{'SCAN_TIMEOUT_SECONDS','SECRETS_CLASS_CHECKS'},'bundled_ops':{'_OWN_REPO_PREFIXES'},'gateway_rpc':{'_PLUGINS_ACTIONS'},'plugin_inspect':{'_RESULT_TTL_SECONDS'},'plugin_presence':{'_TIMEOUT','_MAX_BYTES'},'plugin_remove':{'_PENDING_DELETE_DIR'},'plugin_catalog_gate':{'_SURFACE_LABELS'}}[source]
                for target in node.targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('plugin_settings','plugin_state') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'plugin_settings':{'_FIELD_TYPES','_VALUE_TYPES','_ENV_NAME_CLEAN_RE'},'plugin_state':{'_PLUGIN_SETTING_SEGMENT_RE','_PLUGIN_SETTING_RESERVED_ROOTS'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('env_config','env_routes','web_common') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'env_config':{'_ENV_VAR_NAME_RE','_ENV_VAR_NAME_DENYLIST','_ENV_VAR_NAME_DENY_PREFIXES'},'env_routes':{'_AUTH_TYPE_ENV_VARS','_profile_scope','_config_profile_scope','load_config','save_config','save_env_value','remove_env_value','_CONFIG_MUTATION_LOCK','_DESKTOP_API_MODES','_ROUTE_SECTIONS','_TRANSPORT_ROUTES','_TRANSPORT_LABELS'},'web_common':{'_LEGACY_MASK_RE','REDACTED_CREDENTIAL_WRITE_DETAIL','_profile_scope','_config_profile_scope','_CONFIG_MUTATION_LOCK'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and isinstance(node,ast.ImportFrom) and source=='web_common':
                for alias in node.names:
                    binding=alias.asname or alias.name
                    if binding in {'late','LateState'}:handlers[f'{source}:import:{binding}']=fingerprint(node)
            elif not prefix and source in ('web_deps','web_bootstrap') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in {'web_deps':{'WEB_SERVER'},'web_bootstrap':{'_CONFIG_MUTATION_LOCK'}}[source]:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('credential_pool','pool_sources','auth_middleware') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'credential_pool':{'AUTH_TYPE_OAUTH','AUTH_TYPE_API_KEY','SOURCE_MANUAL','SOURCE_MANUAL_DEVICE_CODE','CUSTOM_POOL_PREFIX','STATUS_OK','STATUS_EXHAUSTED','STATUS_DEAD'},'pool_sources':{'_REGISTRY'},'auth_middleware':{'_GATE_PUBLIC_PREFIXES','_GATE_PUBLIC_ICONS','_SAFE_METHODS','_CROSS_ORIGIN_WRITE_DETAIL','_OTHER_PAGE_WRITE_DETAIL','_SESSION_TOKEN_HEADER','_SESSION_TOKEN_PROVIDER'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('pool_routes','credential_pool','pool_admin','pool_sources','pool_persistence','auth_middleware') and isinstance(node,ast.ImportFrom):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    if source!='pool_routes' or binding in {'config_scoped_to_thread','destructive_profile','CredentialPoolAdd'}:
                        handlers[f'{source}:import:{binding}']=fingerprint(ast.ImportFrom(module=node.module,names=[alias],level=node.level))
            elif not prefix and source in ('fallback_config','config_backend','config_worker_save','provider_identity') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'fallback_config':set(),'config_backend':{'_KEEP_READ_BASE','_REFUSE_LOCKED_OMISSIONS','_PINNED_KEYS'},'config_worker_save':{'_SAME_AS_BASE'},'provider_identity':{'ALIASES'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source=='env_config' and isinstance(node,ast.ImportFrom):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    if binding in {'coerce_provider_id','find_provider_entry','get_compatible_custom_providers'}:
                        handlers[f'{source}:import:{binding}']=fingerprint(ast.ImportFrom(module=node.module,names=[alias],level=node.level))
            elif not prefix and source=='custom_provider_config' and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in {'_CAMEL_ALIASES','_KNOWN_PROVIDER_KEYS'}:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('probe_metadata','probe_route_resolver','profile_helpers') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'probe_metadata':{'_LOCAL_HOSTS','_CONTAINER_LOCAL_SUFFIXES','_TAILSCALE_CGNAT'},'probe_route_resolver':{'_HOST_MANDATED_API_MODES'},'profile_helpers':{'_MODEL_ENTRY_METADATA'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source=='env_routes' and isinstance(node,ast.ImportFrom):
                for alias in node.names:
                    binding=alias.asname or alias.name
                    if binding in {'_denormalize_config_from_web','_normalize_config_for_web','read_raw_config','require_readable_config_before_write','_deep_merge','ConfigUpdate','CustomEndpointUpdate','late','LateState','scoped_to_thread','_validated_main_model_selection','_apply_main_model_assignment','drop_model_key','custom_endpoint_key_env','coerce_provider_id','find_provider_entry','get_compatible_custom_providers','_canonical_api_mode','_custom_provider_entry_to_provider_config','refusing_locked_omissions','_ENV_REF_RE','_parse_model_entries'}:
                        handlers[f'{source}:import:{binding}']=fingerprint(ast.ImportFrom(module=node.module,names=[alias],level=node.level))
            elif not prefix and source in ('oauth_routes','oauth_helpers') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name):handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('oauth_routes','oauth_helpers') and isinstance(node,ast.ImportFrom):
                handlers[f'{source}:import:{node.module}']=fingerprint(node)
            elif not prefix and source in ('model_routes','moa_config') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'model_routes':{'_MOA_PRESET_FIELDS'},'moa_config':{'DEFAULT_MOA_PRESET_NAME','DEFAULT_MOA_REFERENCE_MODELS','DEFAULT_MOA_AGGREGATOR','DEFAULT_MOA_REFERENCE_TIMEOUT','_FLAT_PRESET_KEYS'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('model_config','model_auxiliary') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in {'model_config':{'_AUX_TASK_SLOTS','_BARE_CUSTOM_SELECTORS','_UNSET'},'model_auxiliary':{'_AUX_ROUTE_KEYS','_SESSION_AUXILIARY'}}[source]:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and source in ('model_guards','model_cost','model_data','model_switch') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                selected={'model_guards':{'_GUARDS'},'model_cost':{'INPUT_COST_WARNING_THRESHOLD','OUTPUT_COST_WARNING_THRESHOLD','GPT55_PRO_OPENROUTER_ID','GPT55_SUGGESTION'},'model_data':{'_RULES','_META_CONTRIBUTOR_MESSAGE'},'model_switch':{'_MISSING'}}[source]
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if isinstance(target,ast.Name) and target.id in selected:handlers[f'{source}:{target.id}']=fingerprint(node)
            elif not prefix and isinstance(node,ast.ImportFrom) and source in ('model_defaults','model_aux_runtime'):
                selected={'model_defaults':{'_aux'},'model_aux_runtime':{'_get_auxiliary_task_config'}}[source]
                for alias in node.names:
                    binding=alias.asname or alias.name
                    if binding in selected:handlers[f'{source}:import:{binding}']=fingerprint(node)
            elif not prefix and source in ('model_reasoning','model_defaults') and isinstance(node,(ast.Assign,ast.AnnAssign)):
                targets=node.targets if isinstance(node,ast.Assign) else [node.target]
                for target in targets:
                    if not isinstance(target,ast.Name):continue
                    if source=='model_reasoning' and target.id=='VALID_REASONING_EFFORTS':handlers[f'{source}:{target.id}']=fingerprint(node)
                    if source=='model_defaults' and target.id=='DEFAULT_CONFIG' and isinstance(node.value,ast.Dict):
                        for key,value in zip(node.value.keys,node.value.values):
                            if isinstance(key,ast.Constant) and key.value=='auxiliary':handlers[f'{source}:auxiliary']=fingerprint(value)
            elif source == 'plugin_legacy' and not prefix and isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == 'LEFT_CORE':
                handlers[f'{source}:{node.target.id}'] = fingerprint(node)
            elif source == 'skill_utils' and not prefix and isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == 'ESSENTIAL_SKILLS':
                handlers[f'{source}:{node.target.id}'] = fingerprint(node)
            elif source == 'cron_jobs' and not prefix and isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.target.id == '_CREATE_FIELD_NORMALIZERS':
                handlers[f'{source}:{node.target.id}'] = fingerprint(node)
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                # Strip prose only; all executable statements/defaults/decorators remain in the hash.
                normalized = copy.deepcopy(node)
                if normalized.body and isinstance(normalized.body[0], ast.Expr) and isinstance(normalized.body[0].value, ast.Constant) and isinstance(normalized.body[0].value.value, str):
                    normalized.body = normalized.body[1:]
                handlers[f'{source}:{prefix}{node.name}'] = fingerprint(normalized)
                # RPC modules intentionally name many handlers `_`; preserve their public method identity.
                if source in ('gateway_rpc','gateway_lookup','project_methods') and not prefix:
                    for decorator in node.decorator_list:
                        if (isinstance(decorator,ast.Call) and isinstance(decorator.func,ast.Name)
                                and decorator.func.id in ('_scoped_rpc','_rpc','method','_projects_method') and decorator.args
                                and isinstance(decorator.args[0],ast.Constant) and isinstance(decorator.args[0].value,str)):
                            handlers[f'{source}:rpc:{decorator.args[0].value}'] = fingerprint(normalized)
    visit(tree.body)
    return handlers, fields

class SourceReadError(ValueError):
    def __init__(self, module: str, status: int | None):
        self.module, self.status = module, status
        super().__init__('Pinned upstream module unavailable')


class GitHubReadError(ValueError):
    def __init__(self, status: int | None):
        self.status = status
        super().__init__('GitHub API unavailable; inspect the HTTP status and provider limits')


_REQUEST_INTERVAL = 0.0  # CLI defaults to paced reads; library callers may configure it explicitly.
_LAST_REQUEST_START = None
_REQUEST_LOCK = threading.Lock()


def github_request(argv: list[str]):
    """Space request starts across all download workers; never retry an HTTP refusal."""
    global _LAST_REQUEST_START
    if _REQUEST_INTERVAL:
        with _REQUEST_LOCK:
            now=time.monotonic()
            delay=0 if _LAST_REQUEST_START is None else _LAST_REQUEST_START+_REQUEST_INTERVAL-now
            if delay>0:
                time.sleep(delay)
                now=time.monotonic()
            _LAST_REQUEST_START=now
    return subprocess.run(argv,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30)


def github(path: str, selector: str | None = None) -> dict:
    # Use the supported API and the owner's existing gh authentication/rate budget.
    # Never scrape raw pages, collect a new token or print credentials.
    argv = ['gh', 'api', path]
    if selector:
        argv += ['--jq', selector]
    response = github_request(argv)
    if response.returncode:
        status = re.search(r'HTTP (\d{3})', response.stderr.decode('utf-8', errors='replace'))
        raise GitHubReadError(int(status.group(1)) if status else None)
    if len(response.stdout) > 3_000_000:
        raise ValueError('Upstream response exceeds the audit limit')
    return json.loads(response.stdout)


def resolve(ref: str) -> tuple[str, str]:
    if ref == 'latest':
        ref = github(f'repos/{REPO}/releases/latest', '{tag_name: .tag_name}')['tag_name']
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,120}', ref):
        raise ValueError('Use latest, main, a release tag or a commit SHA')
    sha = github(f'repos/{REPO}/commits/{ref}', '{sha: .sha}')['sha']
    if not re.fullmatch(r'[a-f0-9]{40}', sha):
        raise ValueError('Invalid upstream commit identity')
    return ref, sha

def extract(source: dict[str, str]) -> dict:
    if not (SOURCES.keys() | EXTRA_SOURCES.keys()) <= source.keys():
        raise ValueError('Missing required contract source; manual inspection required')
    trees = {name: ast.parse(value) for name, value in source.items()}
    flags = {}
    constraints = {}
    update_fields = []
    for node in ast.walk(trees['api']):
        if isinstance(node, ast.Assign):
            names = [t.id for t in node.targets if isinstance(t, ast.Name)]
            if '_STATIC_FEATURE_FLAGS' in names:
                flags = ast.literal_eval(node.value)
            for key in ('_MAX_NAME_LENGTH', '_MAX_PROMPT_LENGTH'):
                if key in names:
                    constraints[key] = ast.literal_eval(node.value)
            if '_UPDATE_ALLOWED_FIELDS' in names:
                update_fields = sorted(ast.literal_eval(node.value))
    if not flags or '_MAX_PROMPT_LENGTH' not in constraints:
        raise ValueError('Upstream contract layout changed; manual inspection required')
    routes = set()
    for name in SOURCES:
        tree = trees[name]
        functions = [n for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in ('_http_route_table', '_http_routes')]
        if not functions:
            raise ValueError(f'Cannot identify {name} route registration; manual inspection required')
        for fn in functions:
            for node in ast.walk(fn):
                if isinstance(node, ast.Tuple) and len(node.elts) >= 2:
                    method, path = node.elts[:2]
                    if isinstance(method, ast.Constant) and isinstance(path, ast.Constant) and method.value in ('GET', 'POST', 'PATCH', 'PUT', 'DELETE') and isinstance(path.value, str) and path.value.startswith('/'):
                        routes.add(f'{method.value} {path.value}')
    management_routes, handlers, request_fields = set(), {}, {}
    for name, tree in trees.items():
        found_handlers, found_fields = definitions(tree, name)
        handlers.update(found_handlers); request_fields.update(found_fields)
        if name in MANAGEMENT_SOURCES:
            for node in ast.walk(tree):
                if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                    continue
                for decorator in node.decorator_list:
                    if not isinstance(decorator, ast.Call) or not isinstance(decorator.func, ast.Attribute) or not decorator.args:
                        continue
                    method, path = decorator.func.attr.upper(), decorator.args[0]
                    if method in ('GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'WEBSOCKET') and isinstance(path, ast.Constant) and isinstance(path.value, str) and path.value.startswith('/'):
                        management_routes.add(f'{method} {MANAGEMENT_ROUTE_PREFIXES.get(name, "")}{path.value}')
    # FastAPI supplies this route implicitly; detect only a reviewed literal/default.
    applications=[node for node in ast.walk(trees['web_bootstrap']) if isinstance(node,ast.Call) and isinstance(node.func,ast.Name) and node.func.id=='FastAPI']
    if len(applications)!=1:raise ValueError('Cannot identify the management OpenAPI route')
    configured=next((item for item in applications[0].keywords if item.arg=='openapi_url'),None)
    openapi='/openapi.json' if configured is None else ast.literal_eval(configured.value)
    if openapi is not None:
        if not isinstance(openapi,str) or not openapi.startswith('/') or openapi.startswith('//'):raise ValueError('Invalid management OpenAPI route')
        management_routes.add('GET '+openapi)
    def integer_limit(node):
        if isinstance(node,ast.Constant) and type(node.value) is int:return node.value
        if isinstance(node,ast.BinOp) and isinstance(node.op,ast.Mult):
            result=integer_limit(node.left)*integer_limit(node.right)
            if abs(result)<=1000000:return result
        raise ValueError('Unsupported cron grace limit; inspect upstream without execution')
    grace={}
    for node in trees['cron_time'].body:
        if isinstance(node,ast.Assign):
            for target in node.targets:
                if isinstance(target,ast.Name) and target.id in ('MIN_GRACE_MINUTES','MAX_GRACE_MINUTES'):grace['management_'+target.id.lower()]=integer_limit(node.value)
    if len(grace)!=2:raise ValueError('Missing management cron grace bounds')
    constraints.update(grace)
    return {'routes': sorted(routes), 'management_routes': sorted(management_routes), 'source_hashes': {name: hashlib.sha256(value.encode()).hexdigest() for name,value in source.items()}, 'handlers': handlers, 'request_fields': request_fields,
            'static_features': flags, 'job_constraints': constraints, 'job_update_fields': update_fields}


def github_source(path: str, sha: str) -> str:
    """One pinned file; on a Contents 5xx, verify the official raw representation by Git blob ID."""
    endpoint = f'repos/{REPO}/contents/{path}?ref={sha}'
    try:
        payload = github(endpoint)
    except GitHubReadError as exc:
        if exc.status not in (500, 502, 503, 504):
            raise  # Never retry permissions, missing files or provider rate limits.
        parent = path.rsplit('/', 1)[0]
        listing = github(f'repos/{REPO}/contents/{parent}?ref={sha}')
        if not isinstance(listing, list):
            raise ValueError('Unsupported upstream directory response')
        matches = [row for row in listing if isinstance(row, dict) and row.get('path') == path and row.get('type') == 'file']
        if len(matches) != 1 or not re.fullmatch(r'[a-f0-9]{40}', str(matches[0].get('sha', ''))):
            raise ValueError('Upstream file identity unavailable')
        response = github_request(['gh', 'api', endpoint, '-H', 'Accept: application/vnd.github.raw+json'])
        if response.returncode or len(response.stdout) > 2_000_000:
            raise ValueError('Upstream raw file unavailable or oversized')
        data = response.stdout
        blob = hashlib.sha1(b'blob '+str(len(data)).encode('ascii')+b'\0'+data).hexdigest()
        if blob != matches[0]['sha']:
            raise ValueError('Upstream raw file identity mismatch')
    else:
        if not isinstance(payload, dict) or payload.get('encoding') != 'base64' or payload.get('size', 2_000_001) > 2_000_000:
            raise ValueError('Unsupported upstream content response')
        data = base64.b64decode(payload['content'])
        if len(data) > 2_000_000:
            raise ValueError('Upstream file oversized')
    try:
        return data.decode('utf-8')
    except UnicodeDecodeError as exc:
        raise ValueError('Upstream file is not UTF-8') from exc


def source_identities(commit: str) -> dict:
    """Fetch fresh identities for this exact commit; cached source is never its own authority."""
    require_sha = lambda value: isinstance(value,str) and re.fullmatch(r'[a-f0-9]{40}',value)
    if not require_sha(commit):raise ValueError('Invalid upstream commit')
    wanted=set((SOURCES|EXTRA_SOURCES).values())
    selector='{sha,truncated,tree:[.tree[] | select(.path as $p | '+json.dumps(sorted(wanted))+' | index($p)) | {path,mode,type,sha,size}]}'
    tree=github(f'repos/{REPO}/git/trees/{commit}?recursive=1',selector)
    if (not isinstance(tree,dict) or tree.get('truncated') is not False or not require_sha(tree.get('sha'))
            or not isinstance(tree.get('tree'),list) or len(tree['tree'])>100_000):
        raise ValueError('Upstream source tree unavailable or incomplete')
    found={}
    for row in tree['tree']:
        if not isinstance(row,dict) or not isinstance(row.get('path'),str) or row['path'] not in wanted:continue
        path=row['path']
        if (path in found or row.get('type')!='blob' or row.get('mode') not in ('100644','100755')
                or not require_sha(row.get('sha')) or type(row.get('size')) is not int or not 0<=row['size']<=2_000_000):
            raise ValueError('Invalid upstream source identity')
        found[path]=(row['sha'],row['size'])
    allowed={path for name,path in (SOURCES|EXTRA_SOURCES).items() if reviewed_source_absence(name,path,commit)}
    if wanted-set(found)-allowed:raise ValueError('Upstream source identity missing')
    return found


def cached_github_source(cache: Path,path: str,commit: str,identity: tuple) -> tuple[str,bool]:
    """Only bounded UTF-8 bytes matching a fresh Git tree blob may enter or leave this cache."""
    oid,size=identity
    if not isinstance(oid,str) or not re.fullmatch(r'[a-f0-9]{40}',oid) or type(size) is not int or not 0<=size<=2_000_000:
        raise ValueError('Invalid cached source identity')
    cache.mkdir(parents=True,exist_ok=True)
    if cache.is_symlink():raise ValueError('Source cache must be an owned directory')
    target=cache/oid
    if target.is_symlink():raise ValueError('Cached source cannot be a symlink')
    hit=target.exists()
    if hit:
        if not target.is_file() or target.stat().st_size!=size:raise ValueError('Cached source size mismatch')
        with target.open('rb') as stream:data=stream.read(2_000_001)
    else:
        data=github_source(path,commit).encode('utf-8')
    if len(data)!=size or hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()!=oid:
        raise ValueError('Cached source identity mismatch')
    try:text=data.decode('utf-8')
    except UnicodeDecodeError as exc:raise ValueError('Cached source is not UTF-8') from exc
    if not hit:
        pending=None
        try:
            with tempfile.NamedTemporaryFile(prefix='.hermes-source-',dir=cache,delete=False) as stream:
                pending=Path(stream.name);stream.write(data)
            os.replace(pending,target)
        finally:
            if pending is not None:pending.unlink(missing_ok=True)
    return text,hit


def source_cache_publishable(cache: Path) -> bool:
    """A failed inspection may retain verified blobs, but never its compatibility verdict."""
    try:
        if cache.is_symlink() or not cache.is_dir():return False
        paths=list(cache.iterdir())
        if not paths or len(paths)>512:return False
        total=0
        for path in paths:
            if not re.fullmatch(r'[a-f0-9]{40}',path.name) or path.is_symlink() or not path.is_file():return False
            size=path.stat().st_size;total+=size
            if size>2_000_000 or total>128_000_000:return False
            with path.open('rb') as stream:data=stream.read(2_000_001)
            if len(data)!=size or hashlib.sha1(b'blob '+str(size).encode()+b'\0'+data).hexdigest()!=path.name:return False
            data.decode('utf-8')
        return True
    except (OSError,UnicodeDecodeError):return False


def snapshot(ref: str, source_cache: Path | None = None) -> dict:
    resolved, sha = resolve(ref)
    try:identities=source_identities(sha) if source_cache is not None else None
    except (ValueError,KeyError,OSError,subprocess.TimeoutExpired) as exc:raise SourceReadError('source_tree',getattr(exc,'status',None)) from exc
    cache_counts={'verified_hits':0,'verified_downloads':0}
    failure=[]
    absences=[]
    failure_lock=threading.Lock()
    def absent(name,path):
        with failure_lock:absences.append({'source':name,'path':path,'commit':sha})
        return name,''
    def download(item):
        name, path = item
        with failure_lock:
            if failure:raise failure[0]  # Queued work must not keep hitting an already-refusing provider.
        try:
            if source_cache is None:
                try:return name,github_source(path,sha)
                except GitHubReadError as exc:
                    if exc.status==404 and reviewed_source_absence(name,path,sha):return absent(name,path)
                    raise
            if path not in identities:
                if reviewed_source_absence(name,path,sha):return absent(name,path)
                raise ValueError('Upstream source identity missing')
            value,hit=cached_github_source(source_cache,path,sha,identities[path])
            with failure_lock:cache_counts['verified_hits' if hit else 'verified_downloads']+=1
            return name,value
        except (ValueError, KeyError, OSError, subprocess.TimeoutExpired) as exc:
            with failure_lock:
                if not failure:failure.append(SourceReadError(name,getattr(exc,'status',None)))
                original=failure[0]
            raise original from exc
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        sources = dict(pool.map(download, (SOURCES | EXTRA_SOURCES).items()))
    result={'requested_ref':ref,'resolved_ref':resolved,'commit':sha,'contract':extract(sources),'reviewed_source_absences':sorted(absences,key=lambda entry:entry['source'])}
    if source_cache is not None:result['source_cache']=cache_counts
    return result

def reviewed_handler_replacements(baseline: dict, current: dict, accepted: dict) -> tuple[set, set]:
    """Retired local handlers require exact replacement source bytes and watched dependencies.

    Original handlers remain valid in legacy mode. New local bindings select replacement
    mode, which must be complete; a hybrid cannot silently reuse a legacy fingerprint.
    The review commit records provenance, while source/handler hashes bind later snapshots.
    """
    evidence = baseline.get('reviewed_handler_replacements', {})
    if not isinstance(evidence, dict):
        raise ValueError('Invalid reviewed handler replacements')
    old, new = baseline['upstream']['contract'], current['contract']
    watched = set(baseline.get('mobile_used_handlers', []))
    applied, incomplete = set(), set()
    for key, entries in evidence.items():
        source = key.split(':', 1)[0] if isinstance(key, str) else ''
        if (key not in watched or old.get('handlers', {}).get(key) is None
                or source not in old.get('source_hashes', {})
                or not isinstance(entries, list) or not entries):
            raise ValueError('Invalid retired handler target')
        local_markers, matched = set(), False
        for entry in entries:
            if (not isinstance(entry, dict)
                    or set(entry) != {'commit', 'reason', 'source_sha256', 'replacements'}
                    or not isinstance(entry['commit'], str)
                    or not re.fullmatch(r'[a-f0-9]{40}', entry['commit'])
                    or entry['commit'] == baseline['upstream']['commit']
                    or not isinstance(entry['reason'], str) or not entry['reason'].strip()
                    or not isinstance(entry['source_sha256'], str)
                    or not re.fullmatch(r'[a-f0-9]{64}', entry['source_sha256'])
                    or entry['source_sha256'] == old['source_hashes'][source]
                    or not isinstance(entry['replacements'], dict)
                    or not 1 <= len(entry['replacements']) <= 32):
                raise ValueError('Incomplete retired handler replacement evidence')
            markers = set()
            for replacement, digest in entry['replacements'].items():
                if (replacement not in watched or replacement == key
                        or not isinstance(digest, str) or not re.fullmatch(r'[a-f0-9]{64}', digest)
                        or digest not in ({old.get('handlers', {}).get(replacement)} | accepted.get(replacement, set()))):
                    raise ValueError('Replacement dependencies must be selected and reviewed')
                if replacement.startswith(source + ':') and old.get('handlers', {}).get(replacement) is None:
                    markers.add(replacement)
            if not markers:
                raise ValueError('A retired handler needs a new binding in the same source')
            local_markers.update(markers)
            if (new.get('handlers', {}).get(key) is None
                    and new.get('source_hashes', {}).get(source) == entry['source_sha256']
                    and all(new.get('handlers', {}).get(k) == digest for k, digest in entry['replacements'].items())):
                matched = True
        if matched:
            applied.add(key)
        elif any(marker in new.get('handlers', {}) for marker in local_markers):
            incomplete.add(key)
    return applied, incomplete



def reviewed_handler_variants(baseline: dict, current: dict) -> tuple[dict, set]:
    """Accept reviewed AST variants, optionally bound to complete source dependencies.

    A guarded entry cannot authorize an unchanged AST inside changed module bytes.
    Existing unguarded evidence retains its historical behavior. Guarded reviews
    record exact whole-module identities, not a moving branch or a commit waiver.
    """
    variants = baseline.get('reviewed_handler_variants', {})
    if not isinstance(variants, dict):
        raise ValueError('Invalid reviewed handler variants')
    old, new = baseline['upstream']['contract'], current['contract']
    known_sources = old.get('source_hashes', {})
    accepted, applied = {}, set()
    for key, entries in variants.items():
        if key not in baseline.get('mobile_used_handlers', []) or not isinstance(entries, list):
            raise ValueError('Invalid reviewed handler variant target')
        accepted[key] = set()
        for entry in entries:
            if (not isinstance(entry, dict)
                    or set(entry) not in ({'sha256', 'commit', 'reason'}, {'sha256', 'commit', 'reason', 'required_sources'})
                    or not isinstance(entry.get('sha256'), str) or not re.fullmatch(r'[a-f0-9]{64}', entry['sha256'])
                    or not isinstance(entry.get('commit'), str) or not re.fullmatch(r'[a-f0-9]{40}', entry['commit'])
                    or not isinstance(entry.get('reason'), str) or not entry['reason'].strip()):
                raise ValueError('Incomplete reviewed handler variant evidence')
            if 'required_sources' in entry:
                guards = entry['required_sources']
                if (not isinstance(guards, dict) or not 1 <= len(guards) <= 32
                        or key.split(':', 1)[0] not in guards
                        or any(source not in known_sources or not isinstance(digest, str)
                               or not re.fullmatch(r'[a-f0-9]{64}', digest) for source, digest in guards.items())):
                    raise ValueError('Invalid reviewed handler variant source guards')
                if not all(new.get('source_hashes', {}).get(source) == digest for source, digest in guards.items()):
                    continue
                if new.get('handlers', {}).get(key) == entry['sha256']:
                    applied.add(key)
            accepted[key].add(entry['sha256'])
    return accepted, applied


def compare(baseline: dict, current: dict) -> dict:
    old, new = baseline['upstream']['contract'], current['contract']
    old_routes, new_routes = set(old['routes']), set(new['routes'])
    removed = sorted(old_routes-new_routes)
    used = set(baseline['mobile_used_routes'])
    flags = {key: {'before': old['static_features'].get(key), 'after': new['static_features'].get(key)} for key in sorted(set(old['static_features']) | set(new['static_features'])) if old['static_features'].get(key) != new['static_features'].get(key)}
    constraints = {} if old['job_constraints'] == new['job_constraints'] else {'before': old['job_constraints'], 'after': new['job_constraints']}
    relevant = {'removed_used_routes': sorted(set(removed) & used), 'changed_used_flags': {key: value for key, value in flags.items() if key in baseline['mobile_used_static_flags']}, 'changed_job_constraints': constraints, 'removed_used_job_update_fields': sorted((set(old['job_update_fields']) & set(baseline['mobile_used_job_update_fields'])) - set(new['job_update_fields']))}
    removed_management = sorted(set(old.get('management_routes', []))-set(new.get('management_routes', [])))
    relevant['removed_used_management_routes'] = sorted(set(removed_management) & set(baseline.get('mobile_used_management_routes', [])))
    accepted, guarded_variants = reviewed_handler_variants(baseline, current)
    introduced_absences=baseline.get('reviewed_handler_absences',{})
    if not isinstance(introduced_absences,dict):raise ValueError('Invalid reviewed handler absences')
    allowed_absences={}
    for key,entries in introduced_absences.items():
        if key not in accepted or old.get('handlers',{}).get(key) is not None or not isinstance(entries,list) or not entries:
            raise ValueError('Invalid introduced handler absence target')
        allowed_absences[key]=set()
        for entry in entries:
            if (not isinstance(entry,dict) or not isinstance(entry.get('commit'),str) or not re.fullmatch(r'[a-f0-9]{40}',entry['commit'])
                    or entry['commit']==baseline['upstream']['commit'] or not isinstance(entry.get('reason'),str) or not entry['reason'].strip()):
                raise ValueError('Incomplete introduced handler absence evidence')
            allowed_absences[key].add(entry['commit'])
    replaced, incomplete_replacements = reviewed_handler_replacements(baseline, current, accepted)
    relevant['changed_used_handlers'] = {key: {'before': old.get('handlers', {}).get(key), 'after': new.get('handlers', {}).get(key)}
        for key in baseline.get('mobile_used_handlers', []) if key not in replaced and old.get('handlers', {}).get(key) != new.get('handlers', {}).get(key) and new.get('handlers', {}).get(key) not in accepted.get(key,set())}
    for key in incomplete_replacements:
        relevant['changed_used_handlers'][key] = {'before': old['handlers'][key], 'after': new.get('handlers', {}).get(key), 'unreviewed_replacement_mode': True}
    # An introduced handler disappearing is not a return to the historical baseline. Only the
    # original pin or explicitly reviewed older pins can legitimately lack that selected handler.
    for key in accepted:
        if (old.get('handlers',{}).get(key) is None and new.get('handlers',{}).get(key) is None
                and current.get('commit')!=baseline['upstream']['commit'] and current.get('commit') not in allowed_absences.get(key,set())):
            relevant['changed_used_handlers'][key]={'before':None,'after':None,'required_introduced_handler_missing':True}
    relevant['changed_used_request_fields'] = {key: {'before': old.get('request_fields', {}).get(key), 'after': new.get('request_fields', {}).get(key)}
        for key in baseline.get('mobile_used_request_fields', []) if old.get('request_fields', {}).get(key) != new.get('request_fields', {}).get(key)}
    return {'baseline_commit': baseline['upstream']['commit'], 'reviewed_source_guarded_variants_applied': sorted(guarded_variants), 'reviewed_handler_replacements_applied': sorted(replaced), 'changed_source_modules': sorted(key for key in set(old.get('source_hashes', {})) | set(new.get('source_hashes', {})) if old.get('source_hashes', {}).get(key) != new.get('source_hashes', {}).get(key)), 'known_open_mobile_findings': baseline.get('known_open_mobile_findings', []), 'current': current, 'added_routes': sorted(new_routes-old_routes), 'added_management_routes': sorted(set(new.get('management_routes', []))-set(old.get('management_routes', []))), 'removed_management_routes': removed_management, 'removed_routes': removed, 'changed_static_features': flags, 'job_update_fields_changed': old['job_update_fields'] != new['job_update_fields'], 'mobile_review_required': any(relevant.values()), 'mobile_relevant_changes': relevant, 'limitations': ['Static routes/selected limits, explicitly tracked request fields and handler ASTs; source-guarded variants also bind their selected complete modules. Other transitive helpers, runtime gates, installed deployment and provider behavior still require review and live tests.', 'New routes/features must be triaged even when no used contract changed. No server credentials are sent.']}

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', default='latest')
    parser.add_argument('--baseline', type=Path, default=Path(__file__).resolve().parents[1]/'docs/contracts/hermes-baseline.json')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--snapshot-only', action='store_true')
    parser.add_argument('--source-cache',type=Path,help='Optional public-source blob cache; fresh commit/tree identities and all cached bytes are verified on every run.')
    parser.add_argument('--request-interval',type=float,default=1.0,help='Minimum seconds between GitHub request starts (0–5; default 1).')
    args = parser.parse_args()
    if not math.isfinite(args.request_interval) or not 0<=args.request_interval<=5:
        parser.error('--request-interval must be finite and between 0 and 5 seconds')
    global _REQUEST_INTERVAL, _LAST_REQUEST_START
    _REQUEST_INTERVAL=args.request_interval
    _LAST_REQUEST_START=None
    try:
        current = snapshot(args.ref,args.source_cache)
        result = current if args.snapshot_only else compare(json.loads(args.baseline.read_text(encoding='utf-8')), current)
        text = json.dumps(result, indent=2, ensure_ascii=False)+'\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text, encoding='utf-8')
        else:
            print(text, end='')
        return 2 if result.get('mobile_review_required') else 0
    except (ValueError, KeyError, SyntaxError, OSError, subprocess.TimeoutExpired) as exc:
        context = f' Source module {exc.module}'+(f', HTTP {exc.status}' if exc.status is not None else '')+'.' if isinstance(exc, SourceReadError) else ''
        print(f'Contract check incomplete: {type(exc).__name__}.{context} Inspect upstream/network manually.', file=sys.stderr)
        return 3

if __name__ == '__main__':
    sys.exit(main())
