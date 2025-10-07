[![Release](https://img.shields.io/github/v/release/Rvhoyos/QuackedSMP)](https://github.com/Rvhoyos/QuackedSMP/releases)
![GitHub Downloads](https://img.shields.io/github/downloads/Rvhoyos/QuackedSMP/total)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1360431?label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/quackedsmp)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/quackedsmp?label=Modrinth%20downloads)](https://modrinth.com/mod/quackedsmp)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)


# SMP Essentials

SMP Essentials is a server-side utility plugin, designed as a multiplatform tool for neoforge or Fabric moded Minecraft servers. 
It provides quality of life (QoL) multiplayer (and singleplayer) features such as player homes, spawn teleportation, server rules, land claim protection.

## Features
### Core
- Claims and Teleport commands.
### Player Utilities
- **/home**  
Teleports a player to their saved home location.  
- **/spawn**  
Returns a player to the server spawn.  
- **/rules**  
Displays server rules as a chat message.

- **Welcome Message**  
Displays a welcome message with the players name. 

### Land Claims
- **/claim**  
Protects the player’s current chunk from modification by others.  
- **/unclaim**  
Releases ownership of the current chunk.  
- **/claims**  
Shows how many chunks the player owns and who owns the current one.

### Protection System
Block breaking, placing, and interactions are restricted in claimed chunks to their owners or trusted players.  
> Operators automatically bypass claim limits and all protection checks.

### Claim "trust" lists
- **/trust <player>**  
Adds a player to your trustlist.  
- **/untrust <player>**  
Removes a player from the trustlist.  
- **/trustlist**  
Displays all trusted players.   
Trustlist membership applies globally across all claims owned by a player.

### Configuration and Behavior
- Default claim cap: 50 chunks per player.
- Operators bypass the cap and all restrictions.
- Spawn protection prevents claiming near the server’s shared spawn point.
- Supports all dimensions without special configuration.



#### Next Update:
- Json config for max claims, welcome message and making custom rules.

#### License

Licensed under the Apache License, Version 2.0. You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
