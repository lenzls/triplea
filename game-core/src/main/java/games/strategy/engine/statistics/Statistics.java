package games.strategy.engine.statistics;

import games.strategy.engine.data.*;
import games.strategy.engine.history.EventChild;
import games.strategy.engine.history.History;
import games.strategy.engine.history.HistoryNode;
import games.strategy.engine.history.Round;
import games.strategy.engine.stats.IStat;
import games.strategy.triplea.TripleAUnit;
import games.strategy.triplea.delegate.data.BattleRecord;
import games.strategy.triplea.delegate.data.BattleRecords;
import games.strategy.triplea.ui.AbstractStatPanel;
import games.strategy.triplea.ui.StatPanel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.swing.tree.TreeNode;
import java.util.*;

class Statistics {

    @Getter
    @Setter
    @NoArgsConstructor
    static class BattleStatistics {
        private Map<String, Double> battleTypeCount = new HashMap<>();
        private Map<String, Double> battleSiteCount = new HashMap<>();
        private double totalUnitsLostAttacker = 0;
        private double totalUnitsLostDefender = 0;
    }

    private static class BattleStatMeasurer {

        private final BattleStatistics workingOn = new BattleStatistics();

        void lookAt(BattleRecord battle) {
            String battleType = battle.getBattleType().name();
            Map<String, Double> battleTypeCount = workingOn.getBattleTypeCount();
            double battleTypeCountValue = battleTypeCount.getOrDefault(battleType, 0.0);
            battleTypeCount.put(battleType, battleTypeCountValue + 1);

            String battleSite = battle.getBattleSite().getName();
            Map<String, Double> battleSiteCount = workingOn.getBattleSiteCount();
            double battleSiteCountValue = battleSiteCount.getOrDefault(battleSite, 0.0);
            battleSiteCount.put(battleSite, battleSiteCountValue + 1);

            workingOn.totalUnitsLostAttacker += battle.getAttackerLostTuv();
            workingOn.totalUnitsLostDefender += battle.getDefenderLostTuv();
        }

        BattleStatistics getStatistics() {
            return workingOn;
        }
    }

    @Getter
    private static class HistoryTraverseMeasurer {

        private double totalUnitsLostAttacker = 0;
        private double totalUnitsLostDefender = 0;

        private Map<GamePlayer, Map<UnitType, Integer>> casualtiesPerPlayerPerUnitType;

        HistoryTraverseMeasurer(GameData gameData) {
            casualtiesPerPlayerPerUnitType = new LinkedHashMap<>();
            for (GamePlayer player : gameData.getPlayerList().getPlayers()) {
                casualtiesPerPlayerPerUnitType.put(player, new LinkedHashMap<>());
            }
        }

        private boolean isBattleSummaryNode(HistoryNode node) {
            return node instanceof EventChild &&
                    node.getUserObject() instanceof String &&
                    ((String) node.getUserObject()).startsWith("Battle casualty summary:");
        }

        void lookAt(TreeNode treeNode) {
            HistoryNode node = (HistoryNode) treeNode;
            if (!isBattleSummaryNode(node)) {
                return;
            }
            EventChild battleSummary = (EventChild) node;
            List<TripleAUnit> killed;
            {
                try {
                    killed = (List<TripleAUnit>) battleSummary.getRenderingData();
                }
                catch (ClassCastException e) {
                    throw new IllegalStateException("Did not expect this type in rendering data.", e);
                }
            }
            for (TripleAUnit killedUnit : killed) {
                casualtiesPerPlayerPerUnitType.putIfAbsent(killedUnit.getOwner(), new LinkedHashMap<>());
                casualtiesPerPlayerPerUnitType.get(killedUnit.getOwner()).putIfAbsent(killedUnit.getType(), 0);
                casualtiesPerPlayerPerUnitType.get(killedUnit.getOwner()).compute(killedUnit.getType(), (tripleAUnit, integer) -> integer + 1);
            }
        }
    }

    static BattleStatistics calculateBattleStatistics(GameData gameData) {
        HistoryTraverseMeasurer historyBasedMeasurer = new HistoryTraverseMeasurer(gameData);
        Enumeration<TreeNode> treeNodeEnumeration = ((HistoryNode) gameData.getHistory().getRoot())
                .breadthFirstEnumeration();
        while (treeNodeEnumeration.hasMoreElements()) {
            historyBasedMeasurer.lookAt(treeNodeEnumeration.nextElement());
        }


        BattleStatMeasurer battleStatMeasurer = new BattleStatMeasurer();
        BattleRecordsList battleRecordsList = gameData.getBattleRecordsList();
        battleRecordsList.getBattleRecordsMap().values().stream()
                .flatMap(brs -> BattleRecords.getAllRecords(brs).stream())
                .forEach(battleStatMeasurer::lookAt);
        return battleStatMeasurer.getStatistics();
    }

    private static final Map<Statistic, IStat> defaultGameStatisticOverRoundsMappings = Map.of(
            Statistic.PredefinedStatistic.TUV, new StatPanel.TuvStat(),
            Statistic.PredefinedStatistic.PRODUCTION, new StatPanel.ProductionStat(),
            Statistic.PredefinedStatistic.UNITS, new StatPanel.UnitsStat(),
            Statistic.PredefinedStatistic.VICTORY_CITY, new StatPanel.VictoryCityStat(),
            Statistic.PredefinedStatistic.VP, new StatPanel.VpStat()
    );

    private static Map<Statistic, IStat> createGameStatisticsOverRoundsMapping(List<Resource> resources) {
        Map<Statistic, IStat> result = new HashMap<>(defaultGameStatisticOverRoundsMappings);
        for (Resource resource : resources) {
            result.putIfAbsent(
                    new Statistic.ResourceStatistic(resource),
                    new AbstractStatPanel.ResourceStat(resource)
            );
        }
        return result;
    }

    static Map<Statistic, Map<String, double[]>> calculateGameStatisticsOverRounds(GameData gameData) {
        Map<Statistic, IStat> statisticsMapping = createGameStatisticsOverRoundsMapping(gameData.getResourceList().getResources());
        List<Round> rounds = getRounds(gameData);
        List<GamePlayer> players = gameData.getPlayerList().getPlayers();
        Set<String> alliances = gameData.getAllianceTracker().getAlliances();

        Map<Statistic, Map<String, double[]>> result = new HashMap<>();
        {
            // initialize
            statisticsMapping.keySet().forEach(
                    (statistic) ->  {
                        result.putIfAbsent(statistic, new LinkedHashMap<>());
                        players.forEach(player ->
                                result.get(statistic).put(player.getName(), new double[rounds.size()])
                        );
                        alliances.forEach(alliance ->
                                result.get(statistic).put(alliance, new double[rounds.size()])
                        );
                    }
            );
        }

        for (Round round : rounds) {
            gameData.getHistory().gotoNode(round);

            for (Map.Entry<Statistic, IStat> statistic : statisticsMapping.entrySet()) {
                int roundIndex = round.getRoundNo() - 1;
                for (GamePlayer player : players) {
                    result.get(statistic.getKey()).get(player.getName())[roundIndex] = statistic.getValue().getValue(player, gameData);
                }
                for (String alliance : alliances) {
                    result.get(statistic.getKey()).get(alliance)[roundIndex] = statistic.getValue().getValue(alliance, gameData);
                }
            }
        }

        return result;
    }

    static int calculateNumberOfRounds(History history) {
        HistoryNode root = (HistoryNode) history.getRoot();
        return root.getChildCount();
    }

    private static List<Round> getRounds(GameData gameData) {
        List<Round> rounds = new ArrayList<>();
        HistoryNode root = (HistoryNode) gameData.getHistory().getRoot();
        Enumeration<TreeNode> rootChildren = root.children();
        while (rootChildren.hasMoreElements()) {
            TreeNode child = rootChildren.nextElement();
            if (child instanceof Round) {
                rounds.add((Round) child);
            }
        }
        return rounds;
    }
}
