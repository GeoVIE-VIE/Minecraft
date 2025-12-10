# AICompanions - AI-Powered NPCs for Minecraft

A Paper/Spigot plugin that adds AI-powered NPCs with unique personalities, backstories, faction systems, and dynamic conversations using Claude or OpenAI.

## Features

- **AI-Powered Conversations**: NPCs respond intelligently using Claude or ChatGPT
- **Unique Personalities**: Each NPC has a generated backstory and personality
- **Faction System**: NPCs belong to factions with relationships (hostile, neutral, allied)
- **Wandering Behavior**: NPCs roam the world like Fallout companions
- **Combat & Death**: NPCs can fight each other and die from monsters
- **Memory System**: NPCs remember past conversations with players
- **Quest System**: AI-generated quests from NPCs
- **Persistent**: All data saved to SQLite database

## Requirements

- **Paper/Spigot Server** 1.20+ (NOT vanilla Minecraft)
- **Java 17+**
- **Claude API key** or **OpenAI API key**

## Installation

### Step 1: Switch from Vanilla to Paper

1. Download Paper from https://papermc.io/downloads
2. Replace your `server.jar` with the Paper jar
3. Run once to generate files
4. Your worlds and data will be preserved

### Step 2: Build the Plugin

```bash
cd /path/to/AICompanions
mvn clean package
```

The plugin JAR will be in `target/AICompanions-1.0.0.jar`

### Step 3: Install the Plugin

1. Copy `AICompanions-1.0.0.jar` to your server's `plugins/` folder
2. Start/restart the server
3. Edit `plugins/AICompanions/config.yml` with your API key

### Step 4: Configure API Keys

Edit `plugins/AICompanions/config.yml`:

```yaml
ai:
  # Choose your provider: "claude" or "openai"
  provider: "claude"

  claude:
    api-key: "YOUR_CLAUDE_API_KEY_HERE"  # Get from console.anthropic.com
    model: "claude-sonnet-4-20250514"

  openai:
    api-key: "YOUR_OPENAI_API_KEY_HERE"  # Get from platform.openai.com
    model: "gpt-4o"
```

## Usage

### Talking to NPCs

Use `@` prefix in chat to talk to nearby NPCs:

```
@Hello there!
@Do you have any quests for me?
@Tell me about yourself
```

Or right-click an NPC to see their info, then start chatting.

### Commands

#### NPC Management
| Command | Description |
|---------|-------------|
| `/npc create <name> [faction]` | Create a new NPC |
| `/npc random [faction]` | Create NPC with random name |
| `/npc remove <name>` | Remove an NPC |
| `/npc list [faction]` | List all NPCs |
| `/npc info <name>` | Show NPC details |
| `/npc near` | Show nearby NPCs |
| `/npc tp <name>` | Teleport to NPC |
| `/npc setfaction <name> <faction>` | Change NPC's faction |
| `/npc regenerate <name>` | Regenerate backstory |

#### Faction Management
| Command | Description |
|---------|-------------|
| `/faction create <name> [color] [description]` | Create faction |
| `/faction remove <name>` | Remove faction |
| `/faction list` | List all factions |
| `/faction info <name>` | Faction details |
| `/faction setrelation <f1> <f2> <hostile\|neutral\|allied>` | Set relations |

#### Quests
| Command | Description |
|---------|-------------|
| `/quest list` | Show active quests |
| `/quest info <#>` | Quest details |
| `/quest abandon <#>` | Abandon a quest |

#### AI Configuration
| Command | Description |
|---------|-------------|
| `/aiconfig status` | Show AI status |
| `/aiconfig test` | Test AI connection |
| `/aiconfig reload` | Reload configuration |
| `/aiconfig setprovider <claude\|openai>` | Switch AI provider |

## Default Factions

| Faction | Color | Behavior |
|---------|-------|----------|
| Villagers | Green | Peaceful traders |
| Bandits | Red | Hostile to Villagers & Guards |
| Guards | Blue | Protect against Bandits |
| Merchants | Gold | Wandering traders |
| Wanderers | Gray | Neutral travelers |

## Configuration

See `config.yml` for all options including:

- AI provider settings and rate limits
- NPC wandering distance and behavior
- Combat and vulnerability settings
- Faction relationships
- Memory retention
- Quest rewards

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│   Minecraft     │     │   AICompanions   │     │   AI API    │
│   Server        │────▶│   Plugin         │────▶│   (Claude/  │
│   (Paper)       │◀────│                  │◀────│   OpenAI)   │
└─────────────────┘     └──────────────────┘     └─────────────┘
                               │
                               ▼
                        ┌──────────────┐
                        │   SQLite     │
                        │   Database   │
                        └──────────────┘
```

## Example Interaction

```
Player: @Hello there, stranger!

Aldric Blackwood: *adjusts worn traveling cloak* Greetings, traveler.
These roads aren't safe for those who wander alone. The bandits have
been bolder lately. What brings you to these parts?

Player: @Do you have any work for me?

Aldric Blackwood: *strokes chin thoughtfully* Indeed I do. The bandits
stole a shipment of iron ingots meant for the village smithy. Retrieve
10 iron ingots and I'll make it worth your while.

[Quest Accepted: Iron Recovery - Collect 10 Iron Ingots]
```

## Troubleshooting

### "AI connection failed"
- Verify your API key is correct in config.yml
- Check your API account has credits/quota
- Ensure your server can reach api.anthropic.com or api.openai.com

### NPCs not responding
- Check rate limits in config (default: 10/minute per player)
- Verify you're within interaction distance (default: 5 blocks)
- Use `/aiconfig test` to verify AI connectivity

### NPCs not wandering
- Ensure `npcs.wandering.enabled: true` in config
- Check that NPCs have `canWander: true`

## License

MIT License - Feel free to modify and distribute.

## Credits

Built with:
- [Paper API](https://papermc.io/)
- [Claude API](https://anthropic.com/)
- [OpenAI API](https://openai.com/)
