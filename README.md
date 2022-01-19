# Synopsys Intelligent Orchestration

## Standalone Jenkins Pipeline Scripts

Repository for all standalone Jenkins pipeline scripts (no shared library usage).

| Pipeline Script               | Description                                | Tools                                                                              |
| ----------------------------- | ------------------------------------------ | ---------------------------------------------------------------------------------- |
| IO-Standalone-OS-Go           | Open-source Golang pipeline                | GoSec                                                                              |
| IO-Standalone-OS-Java         | Open-source Java pipeline                  | SpotBugs, Dependency-Check                                                         |
| IO-Standalone-OS-JavaScript   | Open-source JavaScript pipeline            | ESLint, NPM Audit                                                                  |
| IO-Standalone-SNPS-Go         | Go pipeline running Synopsys tools         | Sigma, Polaris, BlackDuck                                                          |
| IO-Standalone-SNPS-Java       | Java pipeline running Synopsys tools       | Sigma, Polaris, BlackDuck                                                          |
| IO-Standalone-SNPS-JavaScript | JavaScript pipeline running Synopsys tools | Sigma, Polaris, BlackDuck                                                          |
| IO-Standalone-Hybrid-Java     | Customizable Java pipeline                 | Sigma, Polaris, SpotBugs, BlackDuck, Dependency-Check (To Do: Coverity, OWASP-ZAP) |