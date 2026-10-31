<!-- Copyright 2026 Google LLC. SPDX-License-Identifier: Apache-2.0 -->

# EGM data handling

EGM is a set of skills, reviewer instructions, and local documentation workflows.
The runtime package does not operate a hosted service, database, analytics client,
or telemetry endpoint. It does not require a separate EGM account or credentials.

## Inputs and outputs

The host assistant reads the project files, design documents, prompts, and review
context supplied for the selected task. Those inputs can contain personal or
confidential information. Provide only the files needed for the review.

EGM writes briefs, design documents, README files, and review reports to the user's
workspace or the host's artifact storage. Outputs can repeat information from
the inputs. Local files persist until the user deletes them; EGM does not apply
an automatic retention period.

## Processing and external services

The selected host processes prompts, files, model responses, and agent activity
under that host's own account settings, terms, and privacy policy. Native agent
calls use the user's host account. EGM does not control host transcript, artifact,
backup, or model-provider retention.

Bootstrap can run local discovery commands and an existing project-configured
formatter. A formatter or other user-approved project command may contact a
package registry or another service. Inspect and authorize those commands using
the host's permission controls. EGM's runtime declares no MCP server and installs
no permission-approval hook.

## Development tools

The source repository also contains opt-in integration tests. When explicitly
enabled, they invoke host CLIs and inspect transcripts produced by their own test
sessions to verify reviewer isolation. They are excluded from runtime release
packages. Website and development tooling are also excluded.

## Questions

Use the [issue tracker](https://github.com/google/elephant-goldfish-model/issues)
for general questions. Do not include credentials, private documents, or personal
data in public reports. This notice describes the plugin's data flow; it does
not replace the privacy policies of the host or services the user invokes.
