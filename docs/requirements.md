Jarvis: A real-time voice assistant powered by cutting-edge LLMs for seamless, intelligent communication and task handling.

active
Category:

ai-solution
Role:

All Roles
Problem Statement

Frontline workers require accurate, reliable, and instantaneous cross-team information. In their high-stakes environment, decisions are made in seconds, leaving zero tolerance for latency or data inaccuracies. We are building an unprecedented and intuitive software+hardware solution to solve this critical operational need.

Functional Requirements

MVP (Minimum Viable Product): Build a voice assistant with persistent memory that meets the following criteria: (1) User Experience Focus: UI is secondary; UX is critical. Design it so that you'd want to use it daily, focusing on a highly capable, low-latency, natural conversation experience. (2) Natural Conversation Cadence: Avoid long pauses and unnatural stretches of silence. Users should know if Jarvis is working – prefer notifying the user audibly if an action will take time. (3) Conversation Memory: It can recall questions and answers from previous sessions (e.g., "Hey Jarvis, yesterday I asked about X – can you remind me what we were talking about?"). (4) Interruptibility: Support interruptions (e.g., "Quiet, Jarvis"). (5) Self-Awareness: It should know its own limitations and functionality, communicating them clearly if asked (e.g., "What can you do, Jarvis?" → "I can pull data from an API, provide information about GitHub repos, etc."). (6) Zero Hallucinations: Ensure completely accurate responses with no fabrications; it should say "I don’t know" instead of making something up. (7) GitHub Integration: It can ingest any public GitHub URL and answer questions about the repository, including open PRs, issues, PR comments, last merges, and more. (8) API Data Handling: It can answer questions based solely on data from a provided API (API spec will be be shared). Note: This data refreshes every 3 minutes, so responses must always use the latest data. Bonus Features: Enhance the MVP with these optional capabilities for extra points: (1) Mobile Compatibility: Build it for mobile using Kotlin or Swift. (2) Passive Mode: Allow the app to run silently in the background, activating only when spoken to. (3) Scalability and Personalization: Handle 10+ simultaneous users. Support user sign-ins, ensuring data isolation per user. Incorporate user preferences (e.g., "NEVER mention X; always flag Y"). (4) End-to-End Agent Capabilities: Enable actions like querying open issues, analyzing them, and automatically opening PRs to fix them (e.g., "Perform analysis of XYZ issue and open a PR to fix it"). This should result in a new PR being opened that attempts to fix the issue.

Required Languages

Programming Languages You are free to use whichever languages and frameworks are most effective for the task (e.g., TypeScript, Python, Kotlin, Go).