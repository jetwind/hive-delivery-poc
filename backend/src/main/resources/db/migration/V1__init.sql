CREATE TABLE delivery_project (
 id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, lifecycle_template_code VARCHAR(100) NOT NULL,
 lifecycle_template_version VARCHAR(50) NOT NULL, status VARCHAR(40) NOT NULL, current_graph_revision INT NOT NULL,
 workspace_path VARCHAR(1000) NOT NULL, planner_session_id VARCHAR(255), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL);
CREATE TABLE delivery_node (
 id UUID PRIMARY KEY, project_id UUID NOT NULL, template_node_id VARCHAR(255), stage_code VARCHAR(20) NOT NULL,
 node_type VARCHAR(30) NOT NULL, title VARCHAR(500) NOT NULL, description TEXT, status VARCHAR(40) NOT NULL,
 executor_type VARCHAR(40) NOT NULL, handler VARCHAR(255), agent_role VARCHAR(255), acceptance_criteria_json TEXT,
 parent_node_id UUID, created_revision INT NOT NULL, superseded_by UUID, sort_order INT NOT NULL);
CREATE INDEX idx_node_project ON delivery_node(project_id);
CREATE TABLE delivery_edge (
 id UUID PRIMARY KEY, project_id UUID NOT NULL, from_node_id UUID NOT NULL, to_node_id UUID NOT NULL,
 edge_type VARCHAR(40) NOT NULL, created_revision INT NOT NULL);
CREATE INDEX idx_edge_project ON delivery_edge(project_id);
CREATE TABLE task_run (
 id UUID PRIMARY KEY, project_id UUID NOT NULL, node_id UUID NOT NULL, attempt INT NOT NULL, status VARCHAR(40) NOT NULL,
 executor_type VARCHAR(40) NOT NULL, external_session_id VARCHAR(255), summary TEXT, changed_files_json TEXT,
 findings_json TEXT, started_at TIMESTAMPTZ NOT NULL, finished_at TIMESTAMPTZ);
CREATE INDEX idx_run_status ON task_run(status);
CREATE TABLE delivery_event (
 id UUID PRIMARY KEY, project_id UUID NOT NULL, event_type VARCHAR(60) NOT NULL, node_id UUID, payload_json TEXT,
 created_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_event_project ON delivery_event(project_id, created_at DESC);
