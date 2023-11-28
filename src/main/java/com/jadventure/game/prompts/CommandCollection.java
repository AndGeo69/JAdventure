package com.jadventure.game.prompts;

import com.jadventure.game.DeathException;
import com.jadventure.game.GameBeans;
import com.jadventure.game.QueueProvider;
import com.jadventure.game.conversation.ConversationManager;
import com.jadventure.game.entities.NPC;
import com.jadventure.game.entities.Player;
import com.jadventure.game.monsters.Monster;
import com.jadventure.game.monsters.MonsterFactory;
import com.jadventure.game.navigation.Coordinate;
import com.jadventure.game.navigation.Direction;
import com.jadventure.game.navigation.ILocation;
import com.jadventure.game.navigation.LocationType;
import com.jadventure.game.repository.ItemRepository;
import com.jadventure.game.repository.LocationRepository;
import com.jadventure.game.repository.NpcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * CommandCollection contains the declaration of the methods mapped to game commands
 *
 * The declared command methods are accessed only by reflection.
 * To declare a new command, add an appropriate method to this class and Annotate it with
 * Command(command, aliases, description)
 */
public enum CommandCollection {
    INSTANCE;

    private final Logger logger = LoggerFactory.getLogger(CommandCollection.class);

    private static Player player;
    private static final Random random = new Random();

    private final static Map<String, String> DIRECTION_LINKS = new HashMap<>();
    static {
        DIRECTION_LINKS.put("n", "north");
        DIRECTION_LINKS.put("s", "south");
        DIRECTION_LINKS.put("e", "east");
        DIRECTION_LINKS.put("w", "west");
        DIRECTION_LINKS.put("u", "up");
        DIRECTION_LINKS.put("d", "down");
    }

    public static CommandCollection getInstance() {
        return INSTANCE;
    }

    public void initPlayer(Player player) {
        CommandCollection.player = player;
    }

    // command methods here

    @Command(command = "help", aliases = "h", description = "Prints help", debug = false)
    public void command_help() {
        Method[] methods = CommandCollection.class.getMethods();
        int commandWidth = 0;
        int descriptionWidth = 0;
        QueueProvider.offer("");

        for (Method method : methods) {
            if (method.isAnnotationPresent(Command.class)) {
                Command annotation = method.getAnnotation(Command.class);
                int currentCommandWidth = calculateCommandWidth(annotation);
                int currentDescriptionWidth = annotation.description().length();

                commandWidth = Math.max(commandWidth, currentCommandWidth);
                descriptionWidth = Math.max(descriptionWidth, currentDescriptionWidth);
            }
        }

        for (Method method : methods) {
            if (method.isAnnotationPresent(Command.class)) {
                Command annotation = method.getAnnotation(Command.class);
                String formattedCommand = formatCommand(annotation);
                String message = String.format("%-" + commandWidth + "s %-" + descriptionWidth + "s",
                        formattedCommand,
                        annotation.description());

                if (shouldDisplayMessage(annotation)) {
                    QueueProvider.offer(message);
                }
            }
        }
    }

    private int calculateCommandWidth(Command annotation) {
        String command = annotation.command();
        if (annotation.aliases().length > 0) {
            command += " (" + String.join(", ", annotation.aliases()) + "):";
        }
        return command.length();
    }

    private String formatCommand(Command annotation) {
        StringBuilder command = new StringBuilder(annotation.command());

        if (annotation.aliases().length > 0) {
            command.append(" (").append(String.join(", ", annotation.aliases())).append("):");
        }

        return command.toString();
    }

    private boolean shouldDisplayMessage(Command annotation) {
        return !annotation.debug() || ("test".equals(player.getName()) && annotation.debug());
    }

    @Command(command="save", aliases={"s"}, description="Save the game", debug=false)
    public void command_save() {
        logger.info("Command 'save' is running");
        player.save();
    }

    @Command(command="monster", aliases={"m", "enemy"}, description="Monsters around you", debug=false)
    public void command_m() {
        List<Monster> monsterList = player.getLocation().getMonsters();
        if (monsterList.size() > 0) {
            QueueProvider.offer("Monsters around you:");
            QueueProvider.offer("----------------------------");
            for (Monster monster : monsterList) {
                QueueProvider.offer(monster.monsterType);
            }
            QueueProvider.offer("----------------------------");
        } else {
            QueueProvider.offer("There are no monsters around you'n");
        }
    }

    @Command(command = "go", aliases = {"g"}, description = "Goto a direction", debug = false)
    public void command_g(String arg) throws DeathException {
        try {
            arg = DIRECTION_LINKS.get(arg);
            Direction direction = Direction.valueOf(arg.toUpperCase());
            movePlayer(direction);
        } catch (IllegalArgumentException | NullPointerException ex) {
            QueueProvider.offer("That direction doesn't exist");
        }
    }

    private void movePlayer(Direction direction) throws DeathException {
        ILocation location = player.getLocation();
        Map<Direction, ILocation> exits = location.getExits();

        if (!exits.containsKey(direction)) {
            QueueProvider.offer("There is no exit that way.");
            return;
        }

        ILocation newLocation = exits.get(direction);

        if (!isValidMove(newLocation)) {
            QueueProvider.offer("You cannot walk through walls.");
            return;
        }

        updatePlayerLocation(newLocation);
        handleLocationEvents();
    }

    private boolean isValidMove(ILocation newLocation) {
        return !newLocation.getLocationType().equals(LocationType.WALL);
    }

    private void updatePlayerLocation(ILocation newLocation) {
        player.setLocation(newLocation);

        if ("test".equals(player.getName())) {
            QueueProvider.offer(player.getLocation().getCoordinate().toString());
        }

        player.getLocation().print();
    }

    private void handleLocationEvents() throws DeathException {

        if (player.getLocation().getMonsters().size() == 0) {
            spawnMonsters(random);
        }

        if (player.getLocation().getItems().size() == 0) {
            int chance = random.nextInt(100);
            if (chance < 60) {
                addItemToLocation();
            }
        }

        if (random.nextDouble() < 0.5) {
            attackRandomMonster();
        }
    }

    private void spawnMonsters(Random random) {
        MonsterFactory monsterFactory = new MonsterFactory();
        int upperBound = random.nextInt(player.getLocation().getDangerRating() + 1);
        for (int i = 0; i < upperBound; i++) {
            Monster monster = monsterFactory.generateMonster(player);
            player.getLocation().addMonster(monster);
        }
    }

    private void attackRandomMonster() throws DeathException {
        List<Monster> monsters = player.getLocation().getMonsters();
        if (monsters.size() > 0) {
            int posMonster = random.nextInt(monsters.size());
            String monster = monsters.get(posMonster).monsterType;
            QueueProvider.offer("A " + monster + " is attacking you!");
            player.attack(monster);
        }
    }


    @Command(command="inspect", aliases = {"i", "lookat"}, description="Inspect an item", debug=false)
    public void command_i(String arg) {
        player.inspectItem(arg.trim());
    }

    @Command(command="equip", aliases= {"e"}, description="Equip an item", debug=false)
    public void command_e(String arg) {
        player.equipItem(arg.trim());
    }

    @Command(command="unequip", aliases={"ue"}, description="Unequip an item", debug=false)
    public void command_ue(String arg) {
        player.dequipItem(arg.trim());
    }

    @Command(command="view", aliases={"v"}, description="View details for 'stats', 'equipped' or 'backpack'", debug=false)
    public void command_v(String arg) {
        arg = arg.trim();
        switch (arg) {
            case "s":
            case "stats":
                player.getStats();
                break;
            case "e":
            case "equipped":
                player.printEquipment();
                break;
            case "b":
            case "backpack":
                player.printStorage();
                break;
            default:
                QueueProvider.offer("That is not a valid display");
                break;
        }
    }

    @Command(command="pick", aliases={"p", "pickup"}, description="Pick up an item", debug=false)
    public void command_p(String arg) {
        player.pickUpItem(arg.trim());
    }

    @Command(command="drop", aliases={"d"}, description="Drop an item", debug=false)
    public void command_d(String arg) {
        player.dropItem(arg.trim());
    }

    @Command(command="attack", aliases={"a"}, description="Attacks an entity", debug=false)
    public void command_a(String arg) throws DeathException {
        player.attack(arg.trim());
    }

    @Command(command="lookaround", aliases={"la"}, description="Displays the description of the room you are in.", debug=false)
    public void command_la() {
        player.getLocation().print();
    }

    // Debug methods here

    @Command(command="attack", aliases={""}, description="Adjusts the damage level the player has", debug=true)
    public void command_attack(String arg) {
        double damage = Double.parseDouble(arg);
        player.setDamage(damage);
    }

    @Command(command="maxhealth", aliases={""}, description="Adjusts the maximum health of the player", debug=true)
    public void command_maxhealth(String arg) {
        int healthMax = Integer.parseInt(arg);
        if (healthMax > 0) {
            player.setHealthMax(healthMax);
        } else {
            QueueProvider.offer("Maximum health must be possitive");
        }
    }

    @Command(command="health", aliases={""}, description="Adjusts the amount of gold the player has", debug=true)
    public void command_health(String arg) {
        int health = Integer.parseInt(arg);
        if (health > 0) {
            player.setHealth(health);
        } else {
            QueueProvider.offer("Health must be possitive");
        }
    }

    @Command(command="armour", aliases={""}, description="Adjusts the amount of armour the player has", debug=true)
    public void command_armour(String arg) {
        int armour = Integer.parseInt(arg);
        player.setArmour(armour);
    }

    @Command(command="level", aliases={""}, description="Adjusts the level of the player", debug=true)
    public void command_level(String arg) {
        int level = Integer.parseInt(arg);
        player.setLevel(level);
    }

    @Command(command="gold", aliases={""}, description="Adjusts the amount of gold the player has", debug=true)
    public void command_gold(String arg) {
        int gold = Integer.parseInt(arg);
        player.setGold(gold);
    }

    @Command(command="teleport", aliases={""}, description="Moves the player to specified coordinates", debug=true)
    public void command_teleport(String arg) {
        LocationRepository locationRepo = GameBeans.getLocationRepository(player.getName());
        ILocation newLocation = locationRepo.getLocation(new Coordinate(arg));
        ILocation oldLocation = player.getLocation();
        try {
            player.setLocation(newLocation);
            player.getLocation().print();
        } catch (NullPointerException e) {
            player.setLocation(oldLocation);
            QueueProvider.offer("There is no such location");
        }
    }

    @Command(command="backpack", aliases={""}, description="Opens the backpack debug menu.", debug=true)
    public void command_backpack(String arg) {
        new BackpackDebugPrompt(player);
    }

    @Command(command="talk", aliases={"t", "speakto"}, description="Talks to a character.", debug=false)
    public void command_talk(String arg) throws DeathException {
        ConversationManager cm = new ConversationManager(NpcRepository.createRepo());
        List<NPC> npcs = player.getLocation().getNpcs();
        NPC npc = null;
        for (NPC i : npcs) {
            if (i.getName().equalsIgnoreCase(arg)) {
                npc = i;
            }
        }
        if (npc != null) {
            cm.startConversation(npc, player);
        } else {
            QueueProvider.offer("Unable to talk to " + arg);
        }
    }

    private void addItemToLocation() {
        ItemRepository itemRepo = GameBeans.getItemRepository();
        if (player.getHealth() < player.getHealthMax()/3) {
            player.getLocation().addItem(itemRepo.getRandomFood(player.getLevel()));
        } else {
            int startIndex = random.nextInt(3);
            switch (startIndex) {
                case 0:
                    player.getLocation().addItem(itemRepo.getRandomWeapon(player.getLevel()));
                    break;
                case 1:
                    player.getLocation().addItem(itemRepo.getRandomFood(player.getLevel()));
                    break;
                case 2:
                    player.getLocation().addItem(itemRepo.getRandomArmour(player.getLevel()));
                    break;
                case 3:
                    player.getLocation().addItem(itemRepo.getRandomPotion(player.getLevel()));
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + startIndex);
            }
        }
    }
}
