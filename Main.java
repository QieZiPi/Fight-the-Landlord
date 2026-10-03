import java.util.*;

/**
 * 斗地主小游戏（命令行单文件版）
 *
 * 编译：javac -encoding UTF-8 Main.java
 * 运行：java Main
 *
 * 本文件包含：Main / Card / CardType / Play / PlayParser / Player / AIPlayer / Game
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("================ 斗地主 ================");
        Game game = new Game();
        game.start();
    }
}

/**
 * 扑克牌：花色 + 点数 + 比较值
 * 比较值: 3=3 ... 10=10 J=11 Q=12 K=13 A=14 2=15 小王=16 大王=17
 */
class Card implements Comparable<Card> {
    private final String suit;   // ♠ ♥ ♣ ♦  小王/大王用 "JOKER"
    private final String rank;   // 3 4 ... 2 small_joker big_joker
    private final int value;     // 比较值

    public Card(String suit, String rank, int value) {
        this.suit = suit;
        this.rank = rank;
        this.value = value;
    }

    public String getSuit() { return suit; }
    public String getRank() { return rank; }
    public int getValue() { return value; }

    @Override
    public int compareTo(Card o) {
        return Integer.compare(this.value, o.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        Card card = (Card) o;
        return value == card.value
                && Objects.equals(suit, card.suit)
                && Objects.equals(rank, card.rank);
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank, value);
    }

    @Override
    public String toString() {
        if ("small_joker".equals(rank)) return "小王";
        if ("big_joker".equals(rank)) return "大王";
        return suit + rank;
    }
}

/**
 * 牌型枚举
 */
enum CardType {
    SINGLE,          // 单张
    PAIR,            // 对子
    TRIPLE,          // 三张
    TRIPLE_SINGLE,   // 三带一
    TRIPLE_PAIR,     // 三带二
    STRAIGHT,        // 顺子
    DOUBLE_STRAIGHT, // 连对
    PLANE,           // 飞机
    PLANE_SINGLE,    // 飞机带单
    PLANE_PAIR,      // 飞机带对
    FOUR_TWO,        // 四带二单
    FOUR_TWO_PAIR,   // 四带两对
    BOMB,            // 炸弹
    ROCKET,          // 王炸
    INVALID          // 非法牌型
}

/**
 * 一次出牌的封装，包含牌、牌型、主值、长度
 */
class Play {
    private final List<Card> cards;
    private final CardType type;
    private final int mainValue; // 主值（用于比较大小）
    private final int length;    // 顺子/连对/飞机的段数

    public Play(List<Card> cards, CardType type, int mainValue, int length) {
        this.cards = cards;
        this.type = type;
        this.mainValue = mainValue;
        this.length = length;
    }

    public List<Card> getCards() { return cards; }
    public CardType getType() { return type; }
    public int getMainValue() { return mainValue; }
    public int getLength() { return length; }

    public boolean isValid() {
        return type != CardType.INVALID;
    }

    /**
     * 判断本次出牌能否压过上一手
     */
    public boolean canBeat(Play other) {
        if (other == null) return true;                  // 首家随意出
        if (type == CardType.ROCKET) return true;        // 王炸最大
        if (other.type == CardType.ROCKET) return false;
        if (type == CardType.BOMB && other.type != CardType.BOMB) return true;
        if (type != CardType.BOMB && other.type == CardType.BOMB) return false;
        if (type != other.type) return false;            // 不同牌型不能压
        if (cards.size() != other.cards.size()) return false;
        if (length != other.length) return false;
        return mainValue > other.mainValue;
    }
}

/**
 * 把一组牌解析成对应的牌型
 */
class PlayParser {

    public static Play parse(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return new Play(cards, CardType.INVALID, 0, 0);
        }
        int n = cards.size();

        // 按点数统计出现次数
        Map<Integer, Integer> countMap = new TreeMap<>();
        for (Card c : cards) {
            countMap.merge(c.getValue(), 1, Integer::sum);
        }

        // 按出现次数反向分组：次数 -> 点数列表
        Map<Integer, List<Integer>> byCount = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : countMap.entrySet()) {
            byCount.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        // 王炸
        if (n == 2 && countMap.containsKey(16) && countMap.containsKey(17)) {
            return new Play(cards, CardType.ROCKET, 17, 1);
        }
        // 炸弹
        if (n == 4 && cnt(byCount, 4) == 1) {
            return new Play(cards, CardType.BOMB, byCount.get(4).get(0), 1);
        }
        // 单张
        if (n == 1) {
            return new Play(cards, CardType.SINGLE, cards.get(0).getValue(), 1);
        }
        // 对子
        if (n == 2 && cnt(byCount, 2) == 1) {
            return new Play(cards, CardType.PAIR, byCount.get(2).get(0), 1);
        }
        // 三张
        if (n == 3 && cnt(byCount, 3) == 1) {
            return new Play(cards, CardType.TRIPLE, byCount.get(3).get(0), 1);
        }
        // 三带一
        if (n == 4 && cnt(byCount, 3) == 1) {
            return new Play(cards, CardType.TRIPLE_SINGLE, byCount.get(3).get(0), 1);
        }
        // 三带二
        if (n == 5 && cnt(byCount, 3) == 1 && cnt(byCount, 2) == 1) {
            return new Play(cards, CardType.TRIPLE_PAIR, byCount.get(3).get(0), 1);
        }
        // 顺子：5+ 张连续单牌，不含 2 和王
        if (n >= 5 && cnt(byCount, 1) == n) {
            List<Integer> singles = byCount.get(1);
            if (isContinuous(singles) && max(singles) <= 14) {
                return new Play(cards, CardType.STRAIGHT, min(singles), n);
            }
        }
        // 连对：3+ 对连续对子，不含 2 和王
        if (n >= 6 && n % 2 == 0 && cnt(byCount, 2) == n / 2) {
            List<Integer> pairs = byCount.get(2);
            if (isContinuous(pairs) && max(pairs) <= 14) {
                return new Play(cards, CardType.DOUBLE_STRAIGHT, min(pairs), n / 2);
            }
        }
        // 飞机（不带翅膀）
        if (n >= 6 && n % 3 == 0 && cnt(byCount, 3) == n / 3) {
            List<Integer> triples = byCount.get(3);
            if (isContinuous(triples) && max(triples) <= 14) {
                return new Play(cards, CardType.PLANE, min(triples), n / 3);
            }
        }
        // 飞机带单
        if (n >= 8 && n % 4 == 0) {
            int planeLen = n / 4;
            List<Integer> triples = byCount.getOrDefault(3, Collections.emptyList());
            if (triples.size() == planeLen && isContinuous(triples) && max(triples) <= 14
                    && cnt(byCount, 1) == planeLen) {
                return new Play(cards, CardType.PLANE_SINGLE, min(triples), planeLen);
            }
        }
        // 飞机带对
        if (n >= 10 && n % 5 == 0) {
            int planeLen = n / 5;
            List<Integer> triples = byCount.getOrDefault(3, Collections.emptyList());
            List<Integer> pairs = byCount.getOrDefault(2, Collections.emptyList());
            if (triples.size() == planeLen && pairs.size() == planeLen
                    && isContinuous(triples) && max(triples) <= 14) {
                return new Play(cards, CardType.PLANE_PAIR, min(triples), planeLen);
            }
        }
        // 四带二单
        if (n == 6 && cnt(byCount, 4) == 1 && cnt(byCount, 1) == 2) {
            return new Play(cards, CardType.FOUR_TWO, byCount.get(4).get(0), 1);
        }
        // 四带两对
        if (n == 8 && cnt(byCount, 4) == 1 && cnt(byCount, 2) == 2) {
            return new Play(cards, CardType.FOUR_TWO_PAIR, byCount.get(4).get(0), 1);
        }

        return new Play(cards, CardType.INVALID, 0, 0);
    }

    private static int cnt(Map<Integer, List<Integer>> byCount, int count) {
        return byCount.getOrDefault(count, Collections.emptyList()).size();
    }

    private static int min(List<Integer> list) {
        return Collections.min(list);
    }

    private static int max(List<Integer> list) {
        return Collections.max(list);
    }

    private static boolean isContinuous(List<Integer> values) {
        if (values.size() <= 1) return true;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) != sorted.get(i - 1) + 1) return false;
        }
        return true;
    }
}

/**
 * 玩家基类
 */
class Player {
    protected final String name;
    protected final List<Card> hand = new ArrayList<>();
    protected boolean landlord;

    public Player(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public List<Card> getHand() { return hand; }
    public boolean isLandlord() { return landlord; }
    public void setLandlord(boolean landlord) { this.landlord = landlord; }

    public void addCards(List<Card> cards) {
        hand.addAll(cards);
        sortHand();
    }

    public void removeCards(List<Card> cards) {
        hand.removeAll(cards);
    }

    public void sortHand() {
        Collections.sort(hand);
    }

    public boolean hasCards() {
        return !hand.isEmpty();
    }

    public int cardCount() {
        return hand.size();
    }

    @Override
    public String toString() {
        return name + (landlord ? "（地主）" : "（农民）");
    }
}

/**
 * 简单 AI：首家出最小牌型，跟牌时找最小能压的牌，否则过
 */
class AIPlayer extends Player {

    public AIPlayer(String name) {
        super(name);
    }

    /**
     * AI 决策出牌
     * @param lastPlay 上一手牌（null 表示首家）
     * @return 出的牌，null 表示过
     */
    public Play decidePlay(Play lastPlay) {
        if (lastPlay == null) {
            return playFirst();
        }
        return playBeat(lastPlay);
    }

    private Map<Integer, List<Card>> groupByValue() {
        Map<Integer, List<Card>> map = new TreeMap<>();
        for (Card c : hand) {
            map.computeIfAbsent(c.getValue(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    // 首家出牌：优先出最小的单张
    private Play playFirst() {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            int v = e.getKey();
            if (v < 16 && e.getValue().size() == 1) {
                return PlayParser.parse(new ArrayList<>(e.getValue()));
            }
        }
        // 没有小单张，出最小的非王单张
        for (Card c : hand) {
            if (c.getValue() < 16) {
                return PlayParser.parse(Collections.singletonList(c));
            }
        }
        // 只剩王，出小王
        if (!hand.isEmpty()) {
            return PlayParser.parse(Collections.singletonList(hand.get(0)));
        }
        return null;
    }

    private Play playBeat(Play lastPlay) {
        switch (lastPlay.getType()) {
            case SINGLE:         return beatSingle(lastPlay.getMainValue());
            case PAIR:           return beatPair(lastPlay.getMainValue());
            case TRIPLE:         return beatTriple(lastPlay.getMainValue());
            case TRIPLE_SINGLE:  return beatTripleSingle(lastPlay.getMainValue());
            case TRIPLE_PAIR:    return beatTriplePair(lastPlay.getMainValue());
            case STRAIGHT:       return beatStraight(lastPlay.getMainValue(), lastPlay.getLength());
            case DOUBLE_STRAIGHT:return beatDoubleStraight(lastPlay.getMainValue(), lastPlay.getLength());
            case PLANE:          return beatPlane(lastPlay.getMainValue(), lastPlay.getLength());
            case BOMB:           return beatBomb(lastPlay.getMainValue());
            default:             return tryBombOrRocket(lastPlay);
        }
    }

    private Play beatSingle(int minValue) {
        for (Card c : hand) {
            if (c.getValue() > minValue) {
                return PlayParser.parse(Collections.singletonList(c));
            }
        }
        return tryBombOrRocket(null);
    }

    private Play beatPair(int minValue) {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > minValue && e.getValue().size() >= 2 && e.getKey() < 16) {
                return PlayParser.parse(new ArrayList<>(e.getValue().subList(0, 2)));
            }
        }
        return tryBombOrRocket(null);
    }

    private Play beatTriple(int minValue) {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > minValue && e.getValue().size() >= 3 && e.getKey() < 16) {
                return PlayParser.parse(new ArrayList<>(e.getValue().subList(0, 3)));
            }
        }
        return tryBombOrRocket(null);
    }

    private Play beatTripleSingle(int minValue) {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > minValue && e.getValue().size() >= 3 && e.getKey() < 16) {
                List<Card> play = new ArrayList<>(e.getValue().subList(0, 3));
                // 随便带一张最小的单
                Card extra = findSmallestSingleExcluding(e.getKey());
                if (extra != null) {
                    play.add(extra);
                    return PlayParser.parse(play);
                }
            }
        }
        return tryBombOrRocket(null);
    }

    private Play beatTriplePair(int minValue) {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > minValue && e.getValue().size() >= 3 && e.getKey() < 16) {
                List<Card> play = new ArrayList<>(e.getValue().subList(0, 3));
                // 找一个最小对子
                for (Map.Entry<Integer, List<Card>> p : byValue.entrySet()) {
                    if (!p.getKey().equals(e.getKey()) && p.getValue().size() >= 2 && p.getKey() < 16) {
                        play.addAll(p.getValue().subList(0, 2));
                        return PlayParser.parse(play);
                    }
                }
            }
        }
        return tryBombOrRocket(null);
    }

    private Play beatStraight(int minStart, int len) {
        // 找长度为 len 的连续单张，起点 > minStart
        for (int start = minStart + 1; start + len - 1 <= 14; start++) {
            boolean ok = true;
            List<Card> play = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                int v = start + i;
                if (!hasSingle(v)) { ok = false; break; }
                play.add(getCardOfValue(v));
            }
            if (ok) return PlayParser.parse(play);
        }
        return tryBombOrRocket(null);
    }

    private Play beatDoubleStraight(int minStart, int len) {
        for (int start = minStart + 1; start + len - 1 <= 14; start++) {
            boolean ok = true;
            List<Card> play = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                int v = start + i;
                if (countOfValue(v) < 2) { ok = false; break; }
                play.addAll(getCardsOfValue(v, 2));
            }
            if (ok) return PlayParser.parse(play);
        }
        return tryBombOrRocket(null);
    }

    private Play beatPlane(int minStart, int len) {
        for (int start = minStart + 1; start + len - 1 <= 14; start++) {
            boolean ok = true;
            List<Card> play = new ArrayList<>();
            for (int i = 0; i < len; i++) {
                int v = start + i;
                if (countOfValue(v) < 3) { ok = false; break; }
                play.addAll(getCardsOfValue(v, 3));
            }
            if (ok) return PlayParser.parse(play);
        }
        return tryBombOrRocket(null);
    }

    private Play beatBomb(int minValue) {
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > minValue && e.getValue().size() == 4) {
                return PlayParser.parse(new ArrayList<>(e.getValue()));
            }
        }
        return tryRocket();
    }

    // 通用：用炸弹或王炸压
    private Play tryBombOrRocket(Play lastPlay) {
        int threshold = (lastPlay != null && lastPlay.getType() == CardType.BOMB)
                ? lastPlay.getMainValue() : 0;
        Map<Integer, List<Card>> byValue = groupByValue();
        for (Map.Entry<Integer, List<Card>> e : byValue.entrySet()) {
            if (e.getKey() > threshold && e.getValue().size() == 4) {
                return PlayParser.parse(new ArrayList<>(e.getValue()));
            }
        }
        return tryRocket();
    }

    private Play tryRocket() {
        boolean small = false, big = false;
        Card s = null, b = null;
        for (Card c : hand) {
            if (c.getValue() == 16) { small = true; s = c; }
            if (c.getValue() == 17) { big = true; b = c; }
        }
        if (small && big) {
            return PlayParser.parse(Arrays.asList(s, b));
        }
        return null;
    }

    private Card findSmallestSingleExcluding(int excludeValue) {
        for (Card c : hand) {
            if (c.getValue() != excludeValue) return c;
        }
        return null;
    }

    private boolean hasSingle(int value) {
        for (Card c : hand) if (c.getValue() == value) return true;
        return false;
    }

    private Card getCardOfValue(int value) {
        for (Card c : hand) if (c.getValue() == value) return c;
        return null;
    }

    private int countOfValue(int value) {
        int n = 0;
        for (Card c : hand) if (c.getValue() == value) n++;
        return n;
    }

    private List<Card> getCardsOfValue(int value, int n) {
        List<Card> result = new ArrayList<>();
        for (Card c : hand) {
            if (c.getValue() == value && result.size() < n) result.add(c);
        }
        return result;
    }
}

/**
 * 游戏主流程：发牌 -> 叫地主 -> 出牌循环 -> 胜负判定
 */
class Game {
    private final List<Card> deck = new ArrayList<>();
    private final Player human;
    private final AIPlayer ai1;
    private final AIPlayer ai2;
    private final Player[] players;
    private Player landlord;
    private final List<Card> bottomCards = new ArrayList<>();
    private final Scanner scanner = new Scanner(System.in);

    public Game() {
        human = new Player("你");
        ai1 = new AIPlayer("电脑左");
        ai2 = new AIPlayer("电脑右");
        players = new Player[]{human, ai1, ai2};
    }

    public void start() {
        createDeck();
        shuffle();
        deal();
        System.out.println("===== 叫地主阶段 =====");
        bidding();
        System.out.println("\n===== 出牌阶段 =====");
        playLoop();
    }

    // 生成一副 54 张牌
    private void createDeck() {
        String[] suits = {"♠", "♥", "♣", "♦"};
        String[] ranks = {"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"};
        int[] values = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        for (int i = 0; i < ranks.length; i++) {
            for (String s : suits) {
                deck.add(new Card(s, ranks[i], values[i]));
            }
        }
        deck.add(new Card("JOKER", "small_joker", 16));
        deck.add(new Card("JOKER", "big_joker", 17));
    }

    private void shuffle() {
        Collections.shuffle(deck);
    }

    // 发牌：每人 17 张，剩 3 张底牌
    private void deal() {
        for (int i = 0; i < 51; i++) {
            players[i % 3].addCards(Collections.singletonList(deck.get(i)));
        }
        for (int i = 51; i < 54; i++) {
            bottomCards.add(deck.get(i));
        }
        System.out.println("底牌：" + bottomCards);
    }

    // 叫地主：人类玩家选择是否叫，不叫则随机给 AI
    private void bidding() {
        System.out.print("你是否叫地主？(y/n): ");
        String input = scanner.nextLine().trim();
        if ("y".equalsIgnoreCase(input)) {
            landlord = human;
        } else {
            // 两个 AI 随机一个当地主
            landlord = new Random().nextBoolean() ? ai1 : ai2;
            System.out.println(landlord.getName() + " 当地主");
        }
        landlord.setLandlord(true);
        landlord.addCards(bottomCards);
        System.out.println(landlord.getName() + " 获得底牌，当前手牌数：" + landlord.cardCount());
    }

    private void playLoop() {
        int current = indexOf(landlord); // 地主先出
        Play lastPlay = null;
        int passCount = 0;

        while (true) {
            Player p = players[current];
            System.out.println("\n——————————————————");
            System.out.println("当前轮到：" + p);
            System.out.println("剩余手牌：" + p.cardCount() + " 张");

            Play play;
            if (p == human) {
                play = humanPlay(lastPlay);
            } else {
                AIPlayer ai = (AIPlayer) p;
                play = ai.decidePlay(lastPlay);
                if (play == null) {
                    System.out.println(p.getName() + "：过");
                } else {
                    System.out.println(p.getName() + " 出牌：" + play.getCards());
                }
            }

            if (play == null) {
                passCount++;
                if (passCount >= 2) {
                    // 连续两家过，重置，下一家自由出
                    lastPlay = null;
                    passCount = 0;
                }
            } else {
                p.removeCards(play.getCards());
                lastPlay = play;
                passCount = 0;

                if (!p.hasCards()) {
                    System.out.println("\n***** " + p.getName() + " 获胜！*****");
                    if (p.isLandlord()) {
                        System.out.println("地主胜利！");
                    } else {
                        System.out.println("农民胜利！");
                    }
                    return;
                }
            }

            current = (current + 1) % 3;
        }
    }

    private int indexOf(Player p) {
        for (int i = 0; i < players.length; i++) {
            if (players[i] == p) return i;
        }
        return -1;
    }

    // 人类玩家出牌
    private Play humanPlay(Play lastPlay) {
        while (true) {
            System.out.println("你的手牌：");
            List<Card> hand = human.getHand();
            for (int i = 0; i < hand.size(); i++) {
                System.out.printf("%d:%s ", i, hand.get(i));
            }
            System.out.println();
            if (lastPlay != null) {
                System.out.println("上一手：" + lastPlay.getCards() + " （牌型：" + lastPlay.getType() + "）");
                System.out.print("输入要出的牌的编号（空格分隔），输入 pass 表示过：");
            } else {
                System.out.print("输入要出的牌的编号（空格分隔）：");
            }

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                System.out.println("输入不能为空");
                continue;
            }
            if ("pass".equalsIgnoreCase(line)) {
                if (lastPlay == null) {
                    System.out.println("首家不能过！");
                    continue;
                }
                return null;
            }

            String[] parts = line.split("\\s+");
            List<Card> selected = new ArrayList<>();
            Set<Integer> seenIndex = new HashSet<>();
            boolean valid = true;
            for (String s : parts) {
                try {
                    int idx = Integer.parseInt(s);
                    if (idx < 0 || idx >= hand.size()) {
                        System.out.println("编号超出范围：" + idx);
                        valid = false;
                        break;
                    }
                    if (!seenIndex.add(idx)) {
                        System.out.println("编号重复：" + idx);
                        valid = false;
                        break;
                    }
                    selected.add(hand.get(idx));
                } catch (NumberFormatException e) {
                    System.out.println("非法输入：" + s);
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            Play play = PlayParser.parse(selected);
            if (!play.isValid()) {
                System.out.println("牌型不合法，请重新输入");
                continue;
            }
            if (lastPlay != null && !play.canBeat(lastPlay)) {
                System.out.println("压不过上一手牌，请重新输入或 pass");
                continue;
            }
            return play;
        }
    }
}
