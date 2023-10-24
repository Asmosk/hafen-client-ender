package haven;


import haven.resutil.WaterTile;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static haven.OCache.posres;

public class ZeeManagerGobClick extends ZeeThread{

    static final int OVERLAY_ID_AGGRO = 1341;

    static Coord coordPc;
    static Coord2d coordMc;
    static Gob gob;
    static String gobName;
    static boolean isGroundClick;

    static float camAngleStart, camAngleEnd, camAngleDiff;
    static long lastClickMouseDownMs, lastClickMouseUpMs, lastClickDiffMs;
    static int lastClickMouseButton;
    static boolean barrelLabelOn = false;
    static boolean isRemovingAllTrees, isDestroyingAllTreelogs;
    private static ArrayList<Gob> treesForRemoval, treelogsForDestruction;
    private static Gob currentRemovingTree, currentDestroyingTreelog;
    static boolean remountClosestHorse;

    public static void startMidClick(Coord pc, Coord2d mc, Gob gobClicked, String gName) {

        lastClickDiffMs = lastClickMouseUpMs - lastClickMouseDownMs;
        coordPc = pc;
        coordMc = mc;
        gob = gobClicked;
        isGroundClick = (gob==null);
        gobName = isGroundClick ? "" : gob.getres().name;

        //println(lastClickDiffMs+"ms > "+gobName + (gob==null ? "" : " dist="+ZeeConfig.distanceToPlayer(gob)));
        //if (gob!=null) println(gobName + " poses = "+ZeeConfig.getGobPoses(gob));

        // long mid-clicks
        if (isLongMidClick()) {
            new ZeeThread(){
                public void run() {
                    runLongMidClick();
                }
            }.start();
        }
        // short mid-clicks
        else {
            // ground clicks
            if(gob==null) {
                // place all pile items
                if(ZeeManagerStockpile.lastPlob != null) {
                    ZeeManagerGobClick.gobPlace(ZeeManagerStockpile.lastPlob,UI.MOD_SHIFT);
                }
                // dig ballclay if cursor dig
                else if(ZeeConfig.isCursorName(ZeeConfig.CURSOR_DIG) && ZeeConfig.isTileNamed(mc, ZeeConfig.TILE_WATER_FRESH_SHALLOW,ZeeConfig.TILE_WATER_OCEAN_SHALLOW)){
                    ZeeConfig.clickTile(ZeeConfig.coordToTile(mc),1,UI.MOD_SHIFT);
                }
                // queue plowing
                else if(ZeeConfig.isPlayerDrivingPlow()){
                    plowQueueAddCoord(coordMc,coordPc);
                }
            }
            // feed clover to wild animal
            else if (checkCloverFeeding(gob)) {
                feedClover(gob);
            }
            // pick quicksilver from smelter
            else if (gobName.endsWith("/smelter") && ZeeConfig.isPlayerHoldingItem()){
                ZeeManagerItemClick.getQuicksilverFromSmelter(gob);
            }
            //barterstand
            else if (gobName.endsWith("barterstand")){
                barterstandSearchWindow();
            }
            // place lifted treelog next to clicked one
            else if ( isGobTreeLog(gobName) && ZeeConfig.isPlayerLiftingGob("gfx/terobjs/trees/")!=null && !ZeeConfig.isPlayerLiftingGob(gob)){
                placeTreelogNextTo(gob);
            }
            // pile boards once and dig more
            else if (gobName.endsWith("/stockpile-board") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_SAW)) {
                ZeeManagerStockpile.pileInvBoardsAndMakeMore(gob);
            }
            // pile blocks once and chop more
            else if (gobName.endsWith("/stockpile-wblock") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_CHOPBLOCK)) {
                ZeeManagerStockpile.pileInvBlocksAndMakeMore(gob);
            }
            // pile sand once and dig more
            else if (gobName.endsWith("/stockpile-sand") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_DIG,ZeeConfig.POSE_PLAYER_DIGSHOVEL)) {
                ZeeManagerStockpile.pileInvSandAndDigMore(gob);
            }
            // pile inv stones and try chipping more stones
            else if (gobName.endsWith("/stockpile-stone") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_PICK, ZeeConfig.POSE_PLAYER_CHIPPINGSTONE)) {
                ZeeManagerStockpile.pileInvStonesAndChipMore(gob);
            }
            // pile inv clay
            else if(gobName.endsWith("/stockpile-clay") && ZeeConfig.isCursorName(ZeeConfig.CURSOR_DIG)){
                ZeeManagerStockpile.pileInvClays(gob);
            }
            // if crating ropes, midclick fibre pile to get more strings and craft again
            else if((gobName.endsWith("/stockpile-flaxfibre") || gobName.endsWith("/stockpile-hempfibre")) && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_ROPE_WALKING)){
                new ZeeThread() {
                    public void run() {
                        ZeeManagerCraft.ropeFetchStringsAndCraft(gob);
                    }
                }.start();
            }
            // click gob holding item (pile, etc)
            else if (ZeeConfig.isPlayerHoldingItem()) {
                clickedGobHoldingItem(gob,gobName);
            }
            // harvest one trellis
            else if (isGobTrellisPlant(gobName)) {
                new ZeeThread() {
                    public void run() {
                        harvestOneTrellis(gob);
                    }
                }.start();
            }
            // pick up all ground items (except when placing stockpile)
            else if (isGobGroundItem(gobName)) {
                if(ZeeManagerStockpile.lastPlob!=null)
                    ZeeConfig.msgLow("click ground to place stockpile");
                else
                    gobClick(gob,3, UI.MOD_SHIFT);//shift + rclick
            }
            // light up torch
            else if (isGobFireSource(gob)) {
                new ZeeThread() {
                    public void run() {
                        if (pickupTorch())
                            itemActGob(gob,0);
                    }
                }.start();
            }
            // mount horse
            else if (isGobHorse(gobName)) {
                new ZeeThread() {
                    public void run() {
                        mountHorse(gob);
                    }
                }.start();
            }
            // label all barrels
            else if (gobName.endsWith("/barrel")) {
                if (barrelLabelOn)
                    ZeeManagerFarmer.testBarrelsTilesClear();
                else
                    ZeeManagerFarmer.testBarrelsTiles(true);
                barrelLabelOn = !barrelLabelOn;
            }
            // pick  dreams from catchers closeby
            else if (gobName.endsWith("/dreca")) {
                pickAllDreamsCloseBy(gob);
            }
            //toggle mine support radius
            else if (isGobMineSupport(gobName)) {
                ZeeConfig.toggleMineSupport();
            }
            // open cauldron
            else if(gobName.contains("/cauldron") && !ZeeConfig.isPlayerLiftingGob(gob)){
                cauldronOpen();
            }
            // open ship cargo
            else if(gobName.endsWith("/knarr") || gobName.endsWith("/snekkja")) {
                new ZeeThread() {
                    public void run() {
                        clickGobPetal(gob,"Cargo");
                    }
                }.start();
            }
            // toggle aggressive gob radius
            else if(ZeeConfig.isAggressive(gobName)){
                toggleOverlayAggro(gob);
            }
            // inspect gob
            else {
                inspectGob(gob);
            }
        }
    }

    private static List<Coord2d> plowQueueCoords = null;
    private static ZeeThread plowQueueThread = null;
    private static void plowQueueAddCoord(Coord2d coordMc, Coord coordPc) {

        // find plow
        Gob plow = ZeeConfig.getClosestGobByNameEnds("/plow");
        if (plow==null){
            println("no plow found");
            return;
        }

        // queue new plow coord
        if (plowQueueCoords ==null){
            plowQueueCoords = new ArrayList<>();
        }
        if (plowQueueCoords.contains(coordMc)){
            println("plow coord already queued");
            return;
        }
        plowQueueCoords.add(coordMc);
        ZeeManagerItemClick.playFeedbackSound();
        ZeeConfig.addPlayerText("plow q "+ plowQueueCoords.size());

        //starts thread
        if (plowQueueThread == null) {
            plowQueueThread = new ZeeThread(){
                public void run() {
                    try {
                        prepareCancelClick();
                        while(!isCancelClick()){
                            sleep(1000);
                            if (isCancelClick()){
                                println("plow queue cancel clicked");
                                break;
                            }
                            //wait plow stops moving
                            if (getGAttrNames(plow).contains("LinMove")){
                                //println("plow lin move");
                                continue;
                            }
                            //wait player stop walking and drinking
                            if (ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_WALK,ZeeConfig.POSE_PLAYER_DRINK)){
                                //println("player walk/drink");
                                continue;
                            }
                            // click next coord
                            if (!plowQueueCoords.isEmpty()){
                                Coord2d nextCoord = plowQueueCoords.remove(0);
                                ZeeConfig.clickCoord(nextCoord.floor(posres),1);
                                prepareCancelClick();
                                //drink
                                if (ZeeConfig.getStamina() < 100) {
                                    ZeeManagerItemClick.drinkFromBeltHandsInv();
                                }
                                ZeeConfig.addPlayerText("plow q "+ plowQueueCoords.size());
                            }
                            else {
                                println("plow queue ended");
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        println("thread queue plow interrupted");
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    ZeeConfig.removePlayerText();
                    plowQueueReset();
                }
            };
        }
        if (!plowQueueThread.isAlive()) {
            plowQueueThread.start();
        }
    }

    static void plowQueueReset(){
        plowQueueCoords = null;
        plowQueueThread = null;
    }


    static void barterSearchUpdateGobs() {
        List<Gob> stands = ZeeConfig.findGobsByNameEndsWith("/barterstand");
        if (stands.size() == 0)
            return;
        for (Gob stand : stands) {
            synchronized (stand) {
                //remove barterstand text
                Gob.Overlay ol = stand.findol(ZeeGobText.class);
                if (ol != null) {
                    ol.remove(false);
                }
                //add barter text
                addTextBarterStand(stand);
            }
        }
    }
    static void addTextBarterStand(Gob ob) {

        if (!barterSearchOpen)
            return;

        List<String> foundItems = new ArrayList<>();
        List<String> barterItems = getBarterstandItems(ob);

        // checkboxes "ore", "stone"
        if (barterFindCheckOre || barterFindCheckStone) {
            for (String barterItem : barterItems) {
                // found generic "stone"
                if (barterFindCheckStone && !foundItems.contains("stone") && ZeeConfig.mineablesStone.contains(barterItem)) {
                    foundItems.add("stone");
                }
                // found generic "ore"
                if (barterFindCheckOre && !foundItems.contains("ore") && (ZeeConfig.mineablesOre.contains(barterItem) || ZeeConfig.mineablesOrePrecious.contains(barterItem))) {
                    foundItems.add("ore");
                }
            }
        }

        // keywords from text area
        if (barterFindText != null && !barterFindText.strip().isBlank()) {
            String[] arrKeywords = barterFindText.strip().split(" ");
            for (String keyword : arrKeywords) {
                for (String barterItem : barterItems) {
                    // found specific keyword
                    if (barterItem.contains(keyword) && !foundItems.contains(barterItem))
                    {
                        foundItems.add(barterItem);
                    }
                }
            }
        }

        //add found names to barterstand
        if (foundItems.size() > 0){
            ZeeConfig.addGobText(ob, foundItems.toString());
        }
    }
    static String barterFindText;
    static boolean barterSearchOpen = false;
    static boolean barterFindCheckStone = false;
    static boolean barterFindCheckOre = false;
    static void barterstandSearchWindow() {

        Widget wdg;
        String title = "Find Stand Item";

        Window win = ZeeConfig.getWindow(title);
        if (win != null){
            win.reqdestroy();
            win = null;
        }

        //create window
        win = ZeeConfig.gameUI.add(
            new Window(Coord.of(120,70),title){
                public void wdgmsg(String msg, Object... args) {
                    if (msg.contentEquals("close")){
                        barterSearchOpen = false;
                        barterFindText = null;
                        for (Gob stand : ZeeConfig.findGobsByNameEndsWith("/barterstand")) {
                            ZeeConfig.removeGobText(stand);
                        }
                        this.reqdestroy();
                    }
                }
            },
            300,300
        );
        barterSearchOpen = true;

        //label
        wdg = win.add(new Label("keywords (space sep.)"));

        //text entry
        wdg = win.add(new TextEntry(UI.scale(130),""){
            public void activate(String text) {
                // update barterstand labels
                barterSearchUpdateGobs();
            }
            public boolean keyup(KeyEvent ev) {
                barterFindText = this.text();
                return true;
            }
        },0,wdg.c.y+wdg.sz.y);

        //checkbox stones
        wdg = win.add(new CheckBox("stone"){
            public void changed(boolean val) {
                barterFindCheckStone = val;
                barterSearchUpdateGobs();
            }
        },0,wdg.c.y+wdg.sz.y+5);

        //checkbox ore
        wdg = win.add(new CheckBox("ore"){
            public void changed(boolean val) {
                barterFindCheckOre = val;
                barterSearchUpdateGobs();
            }
        },wdg.c.x+wdg.sz.x+5,wdg.c.y);

        win.pack();
    }
    static List<String> getBarterstandItems(Gob barterStand) {
        List<String> ret = new ArrayList<>();
        for (Gob.Overlay ol : barterStand.ols) {
            if(ol.spr.getClass().getName().contentEquals("Equed")) {
                try {
                    Field f = ol.spr.getClass().getDeclaredField("espr");
                    f.setAccessible(true);
                    Sprite espr = (Sprite) f.get(ol.spr);
                    ret.add(espr.res.basename());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return ret;
    }


    // place treelog next to other
    // TODO currently only works if player is perpendicular to treeLogGround
    // TODO may break depending on coords signal change
    private static void placeTreelogNextTo(Gob treeLogGround) {
        new Thread() {
            public void run() {
                try {

                    ZeeConfig.addPlayerText("placing");

                    Gob liftedTreelog = ZeeConfig.isPlayerLiftingGob("gfx/terobjs/trees/");
                    if (liftedTreelog==null){
                        ZeeConfig.msgError("placeTreelogNextTo > couldn't find lifted treelog");
                        ZeeConfig.removePlayerText();
                        return;
                    }
                    //println(liftedTreelog.rc +" "+liftedTreelog.a+"  ,  "+treeLogGround.rc+" "+treeLogGround.a);

                    // right click lifted treelog to create plob
                    gobClick(liftedTreelog,3);
                    sleep(500);

                    // adjust plob angle, postition and place it
                    if (ZeeManagerStockpile.lastPlob==null){
                        ZeeConfig.msgError("placeTreelogNextTo > couldn't find last plob");
                        ZeeConfig.removePlayerText();
                        return;
                    }
                    Coord2d playerrc = ZeeConfig.getPlayerGob().rc;
                    Coord2d newrc = Coord2d.of(treeLogGround.rc.x, treeLogGround.rc.y);
                    if (Math.abs(treeLogGround.rc.x - playerrc.x) > Math.abs(treeLogGround.rc.y - playerrc.y)){
                        if (treeLogGround.rc.x > playerrc.x)
                            newrc.x -= 4.125;
                        else
                            newrc.x += 4.125;
                    }else{
                        if (treeLogGround.rc.y > playerrc.y)
                            newrc.y -= 4.125;
                        else
                            newrc.y += 4.125;
                    }
                    // position plob
                    ZeeManagerStockpile.lastPlob.move(newrc, treeLogGround.a);

                    // place treelog and wait
                    gobPlace(ZeeManagerStockpile.lastPlob,0);
                    waitNotPlayerPose(ZeeConfig.POSE_PLAYER_LIFTING);

                }catch (Exception e){
                    e.printStackTrace();
                }
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    private static boolean checkCloverFeeding(Gob animal) {

        if (ZeeManagerItemClick.getHoldingItem()==null) {
            //println("checkCloverFeeding > holding item null");
            return false;
        }

        GItem holditem = ZeeManagerItemClick.getHoldingItem().item;

        if (ZeeManagerItemClick.isStackByKeyPagina(holditem)) {
            //println("checkCloverFeeding > holding stack");
            return false;
        }

        if (holditem!=null && holditem.getres().name.endsWith("/clover")){
            List<String> endList = List.of("/cattle", "/sheep","/horse","/boar","/wildgoat");
            for (String s : endList) {
                if (animal.getres().name.endsWith(s))
                    return true;
            }
        }else{
            //println("checkCloverFeeding > item null or name wrong");
        }

        //println("checkCloverFeeding > ret false");
        return false;
    }

    private static void feedClover(Gob animal){
        if (animal==null) {
            //println("feedClover > animal gob null");
            return;
        }
        new ZeeThread(){
            public void run() {
                try {
                    ZeeConfig.addPlayerText("clovering");
                    double dist = ZeeConfig.distanceToPlayer(animal);

                    prepareCancelClick();

                    //click animal location until distance close enough
                    while (dist > 50 && !isCancelClick()){
                        ZeeConfig.clickCoord(ZeeConfig.getGobCoord(animal),1);
                        prepareCancelClick();
                        sleep(777);
                        dist = ZeeConfig.distanceToPlayer(animal);
                    }

                    //println("dist "+dist);

                    // try feeding clover
                    if (!isCancelClick()) {
                        //println("final click");
                        itemActGob(animal, 0);
                    }
                    //else println("final click canceled");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    public static void checkPlobUnplaced() {
        if (ZeeConfig.autoToggleGridLines)
            ZeeConfig.gameUI.map.showgrid(false);

        ZeeManagerStockpile.lastPlob = null;
    }

    static void runLongMidClick() {
        try {
            /*
                ground clicks
             */
            if (isGroundClick){
                //dismount horse
                if (ZeeConfig.isPlayerMountingHorse()) {
                    dismountHorse(coordMc);
                }
                //clicked water
                else if (isWaterTile(coordMc)) {
                    showGobFlowerMenu();
                }
                //disembark water vehicles
                else if (ZeeConfig.isPlayerOnCoracle()) {
                    disembarkEquipCoracle(coordMc);
                }
                else if(ZeeConfig.isPlayerOnDugout()  || ZeeConfig.isPlayerOnRowboat()) {
                    disembarkBoatAtShore(coordMc);
                }
                //disembark kicksled
                else if(ZeeConfig.isPlayerDrivingingKicksled()){
                    disembarkVehicle(coordMc);
                }
                //unload wheelbarrow at tile
                else if (ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    ZeeManagerStockpile.unloadWheelbarrowStockpileAtGround(coordMc.floor(posres));
                    if (ZeeConfig.autoToggleGridLines)
                        ZeeConfig.gameUI.map.showgrid(true);
                }
                // clear snow area
                else if (ZeeConfig.getTileResName(coordMc).contains("tiles/snow")){
                    //haven.MapView@11460448 ; click ; [(629, 490), (1014904, 1060429), 3, 1]
                    ZeeConfig.clickCoord(coordMc.floor(posres),3,UI.MOD_SHIFT);
                }
                else{
                    showGobFlowerMenu();
                }
            }
            /*
                non-ground clicks
            */
            // pile boards from treelog
            else if (gobName.endsWith("/stockpile-board") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_SAW)) {
                ZeeManagerStockpile.pileBoardsFromTreelog(gob);
            }
            // pile blocks from treelog
            else if (gobName.endsWith("/stockpile-wblock") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_CHOPBLOCK)) {
                ZeeManagerStockpile.pileBlocksFromTreelog(gob);
            }
            // pile sand from tile
            else if (gobName.endsWith("/stockpile-sand") && ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_DIG,ZeeConfig.POSE_PLAYER_DIGSHOVEL)) {
                ZeeManagerStockpile.pileSandFromSandTile(gob);
            }
            // put out cauldron
            else if(gobName.contains("/cauldron") && !ZeeConfig.isPlayerLiftingGob(gob)){
                cauldronPutOut();
            }
            // schedule tree removal
            else if (isRemovingAllTrees && isGobTree(gobName)) {
                scheduleRemoveTree(gob);
            }
            // schedule treelog destruction
            else if (isDestroyingAllTreelogs && isGobTreeLog(gobName)) {
                scheduleDestroyTreelog(gob);
            }
            // show ZeeFlowerMenu
            else if (!isGroundClick && !ZeeConfig.isPlayerHoldingItem() && showGobFlowerMenu()) {

            }
            // activate cursor harvest
            else if (isGobCrop(gobName)) {
                if (!ZeeConfig.getCursorName().equals(ZeeConfig.CURSOR_HARVEST))
                    gobClick(gob, 3, UI.MOD_SHIFT);
            }
            // stockpile + wheelbarrow
            else if (isGobStockpile(gobName)) {
                // driving wheelbarrow = start piles mover
                if (ZeeConfig.isPlayerDrivingWheelbarrow()) {
                    ZeeManagerStockpile.startPilesMover();
                }
                // carrying wheelbarrow = use wb on pile
                else if(ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    ZeeManagerStockpile.useWheelbarrowAtStockpile(gob);
                    if (ZeeConfig.autoToggleGridLines)
                        ZeeConfig.gameUI.map.showgrid(true);
                }
                //pickup all pile items
                else {
                    gobClick(gob,3, UI.MOD_SHIFT);
                    ZeeManagerItemClick.playFeedbackSound();
                }
            }
            // pickup all items: dframe
            else if (gobName.endsWith("/dframe")) {
                gobClick(gob,3, UI.MOD_SHIFT);
            }
            // remove tree stump
            else if (isGobTreeStump(gobName)) {
                removeStumpMaybe(gob);
            }
            // item act barrel
            else if (ZeeConfig.isPlayerHoldingItem() && gobName.endsWith("/barrel")) {
                if (ZeeManagerFarmer.isBarrelEmpty(gob))
                    itemActGob(gob,UI.MOD_SHIFT);//shift+rclick
                else
                    itemActGob(gob,3);//ctrl+shift+rclick
            }
            // player lifting wheelbarrow
            else if (ZeeConfig.isPlayerCarryingWheelbarrow()) {
                // mount horse and liftup wb
                if (isGobHorse(gobName)) {
                    mountHorseCarryingWheelbarrow(gob);
                }
                // unload wb at gob
                else {
                    unloadWheelbarrowAtGob(gob);
                    if (ZeeConfig.autoToggleGridLines)
                        ZeeConfig.gameUI.map.showgrid(true);
                }
            }
            // player driving wheelbarrow
            else if (!gobName.endsWith("/wheelbarrow") && ZeeConfig.isPlayerDrivingWheelbarrow()) {
                // mount horse and liftup wb
                if (isGobHorse(gobName))
                    mountHorseDrivingWheelbarrow(gob);
                // lift up wb and open gate
                else if (isGobGate(gobName))
                    openGateWheelbarrow(gob);
                // lift up wb and store in cart
                else if (gobName.endsWith("/cart")) {
                    Gob wb = ZeeConfig.getClosestGobByNameContains("/wheelbarrow");
                    liftGobAndClickTarget(wb,gob);
                }
            }
            // drive ship
            else if(gobName.endsWith("/knarr") || gobName.endsWith("/snekkja")) {
                clickGobPetal(gob,"Man the helm");
            }
            // lift up gob
            else if (isGobLiftable(gobName) || isGobBush(gobName)) {
                liftGob(gob);
            }
            // gob item piler
            else if (ZeeManagerStockpile.isGobPileable(gob)){
                ZeeManagerStockpile.areaPilerWindow(gob);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    static void cauldronOpen() {
        new ZeeThread(){
            public void run() {
                try {
                    // dismount horse/kicksled
                    if (ZeeConfig.isPlayerMountingHorse() || ZeeConfig.isPlayerDrivingingKicksled()) {
                        Coord cauldronCoord = ZeeConfig.lastMapViewClickMc.floor(posres);
                        ZeeManagerGobClick.disembarkVehicle(cauldronCoord);
                        sleep(777);
                    }
                    // open cauldron
                    clickGobPetal(gob,"Open");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    static void cauldronPutOut() {
        // no new thread needed, as long as caller already created one
        try {
            // dismount horse/kicksled
            if (ZeeConfig.isPlayerMountingHorse() || ZeeConfig.isPlayerDrivingingKicksled()) {
                Coord cauldronCoord = ZeeConfig.lastMapViewClickMc.floor(posres);
                ZeeManagerGobClick.disembarkVehicle(cauldronCoord);
                sleep(777);
            }
            // "put out" cauldron
            clickGobPetal(gob,"Put out");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    static void disembarkBoatAtShore(Coord2d mc){
        try {
            ZeeConfig.addPlayerText("boatin");
            //move to shore
            ZeeConfig.clickTile(ZeeConfig.coordToTile(coordMc), 1);
            waitPlayerPoseNotInList(
                    ZeeConfig.POSE_PLAYER_DUGOUT_ACTIVE,
                    ZeeConfig.POSE_PLAYER_ROWBOAT_ACTIVE,
                    ZeeConfig.POSE_PLAYER_CORACLE_ACTIVE
            );//TODO add snekkja, knarr?
            //disembark
            ZeeConfig.clickTile(ZeeConfig.coordToTile(mc), 1, UI.MOD_CTRL);
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    static void disembarkEquipCoracle(Coord2d coordMc){
        try {
            ZeeConfig.addPlayerText("coracling");
            //move to shore
            ZeeConfig.clickTile(ZeeConfig.coordToTile(coordMc),1);
            waitPlayerPose(ZeeConfig.POSE_PLAYER_CORACLE_IDLE);
            //disembark
            ZeeConfig.clickTile(ZeeConfig.coordToTile(coordMc),1,UI.MOD_CTRL);
            sleep(PING_MS*2);
            if (ZeeConfig.isPlayerOnCoracle()){
                println("couldn't dismount coracle");
                ZeeConfig.removePlayerText();
                return;
            }
            //find coracle
            Gob coracle = ZeeConfig.getClosestGobByNameContains("/coracle");
            if (coracle == null) {
                println("couldn't find gob coracle");
                ZeeConfig.removePlayerText();
                return;
            }
            //try pickup coracle, if cape slot empty
            clickGobPetal(coracle,"Pick up");
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    static void dropEmbarkCoracle(Coord2d waterMc) {
        try {
            ZeeConfig.addPlayerText("coracling");

            //wait player reach water
            Gob player = ZeeConfig.getPlayerGob();
            long timeout = 5000;
            ZeeConfig.clickTile(ZeeConfig.coordToTile(waterMc),1);
            waitNotPlayerPose(ZeeConfig.POSE_PLAYER_IDLE);
            while(!ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_IDLE) && !isWaterTile(player.rc)){
                if (timeout<=0){
                    println("couldn't reach water tile");
                    ZeeConfig.removePlayerText();
                    return;
                }
                timeout -= PING_MS;
                sleep(PING_MS);
            }

            if (ZeeConfig.isCancelClick()){
                ZeeConfig.removePlayerText();
                return;
            }

            //drop coracle at shalow water or terrain
            ZeeManagerItemClick.getEquipory().dropItemByNameContains("gfx/invobjs/small/coracle");
            ZeeConfig.stopMovingEscKey();
            waitNotPlayerPose(ZeeConfig.POSE_PLAYER_CORACLE_CAPE);


            //find coracle gob
            Gob coracle = ZeeConfig.getClosestGobByNameContains("/coracle");
            if (coracle == null) {
                println("couldn't find gob coracle");
                ZeeConfig.removePlayerText();
                return;
            }

            //if dropped tile is not water
            if (!isWaterTile(ZeeConfig.getGobTile(coracle))){
                //lift up coracle
                liftGob(coracle);
                sleep(PING_MS);
                if (ZeeConfig.isCancelClick()){
                    ZeeConfig.removePlayerText();
                    return;
                }
                // place coracle at water tile
                ZeeConfig.clickTile(ZeeConfig.coordToTile(waterMc),3);
                waitPlayerIdlePose();
                if (ZeeConfig.distanceToPlayer(coracle)==0){
                    // player blocked by deep water tile
                    Coord pc = ZeeConfig.getPlayerCoord();
                    Coord subc = ZeeConfig.coordToTile(waterMc).sub(pc);
                    int xsignal, ysignal;
                    xsignal = subc.x >= 0 ? -1 : 1;
                    ysignal = subc.y >= 0 ? -1 : 1;
                    //try to drop coracle torwards clicked water coord
                    ZeeConfig.clickCoord(pc.add(xsignal * 300, ysignal * 300), 3);
                    sleep(PING_MS*2);
                    if (ZeeConfig.isCancelClick()){
                        ZeeConfig.removePlayerText();
                        return;
                    }
                    if (ZeeConfig.distanceToPlayer(coracle)==0) {
                        println("failed dropping to deep water?");
                        ZeeConfig.removePlayerText();
                        return;
                    }
                }
            }

            //mount coracle
            clickGobPetal(coracle, "Into the blue yonder!");
            waitPlayerPose(ZeeConfig.POSE_PLAYER_CORACLE_IDLE);

        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private static void inspectWaterAt(Coord2d coordMc) {

        // require wooden cup
        Inventory inv = ZeeConfig.getMainInventory();
        List<WItem> cups = inv.getWItemsByNameContains("/woodencup");
        if (cups==null || cups.size()==0){
            ZeeConfig.msgError("need woodencup to inspect water");
            return;
        }

        // pickup inv cup, click water, return cup
        WItem cup = cups.get(0);
        ZeeManagerItemClick.pickUpItem(cup);
        ZeeConfig.itemActTile(coordMc.floor(posres));
        waitPlayerIdleFor(1);

        // show msg
        String msg = ZeeManagerItemClick.getHoldingItemContentsNameQl();
        ZeeConfig.msgLow(msg);
        ZeeSynth.textToSpeakLinuxFestival(msg.replaceAll("\\D",""));
        new ZeeThread(){
            public void run() {
                ZeeConfig.addPlayerText(msg);
                // wait click before removing player text
                waitMapClick();
                ZeeConfig.removePlayerText();
            }
        }.start();
        //haven.ChatUI$MultiChat@dd1ed65 ; msg ; ["hello world"]

        //empty cup
        Coord cupSlot = ZeeManagerItemClick.dropHoldingItemToInvAndRetCoord(inv);
        if (cupSlot!=null) {
            cup = inv.getItemBySlotCoord(cupSlot);
            boolean confirmPetalBackup = ZeeConfig.confirmPetal;
            ZeeConfig.confirmPetal = false;//temp disable confirm petal
            ZeeManagerItemClick.clickItemPetal(cup, "Empty");
            ZeeConfig.confirmPetal = confirmPetalBackup;
        }
    }

    public static boolean isWaterTile(Coord2d coordMc) {
        return isWaterTile(coordMc.floor(MCache.tilesz));
    }

    public static boolean isWaterTile(Coord tile) {
        Tiler t = ZeeConfig.getTilerAt(tile);
        return t!=null && t instanceof WaterTile;
    }


    static boolean clickedPlantGobForLabelingQl = false;
    static Gob plantGobForLabelingQl;
    static void labelHarvestedPlant(String clickedPetal){

        if ( !clickedPlantGobForLabelingQl || !clickedPetal.contentEquals("Harvest"))
            return;

        if(ZeeConfig.getMainInventory().getNumberOfFreeSlots() == 0){
            println("labelHarvestedPlant > inv full");
            return;
        }

        if( ZeeConfig.lastInvItemMs > ZeeConfig.lastMapViewClickMs &&
            !ZeeConfig.lastInvItemName.endsWith("plants/wine") &&
            !ZeeConfig.lastInvItemName.endsWith("plants/hops") &&
            !ZeeConfig.lastInvItemName.endsWith("plants/pepper"))
        {
            println("labelHarvestedPlant > invalid lastInvItem name ("+ZeeConfig.lastInvItemName+")");
            return;
        }

        //save plant gob
        plantGobForLabelingQl = ZeeConfig.lastMapViewClickGob;

        new ZeeThread(){
            public void run() {
                try{
                    ZeeConfig.addPlayerText("inspect ql");
                    long t1 = ZeeThread.now();
                    //wait approaching plant
                    if(waitPlayerIdlePose()) {
                        //wait inventory harvested item
                        ZeeConfig.lastMapViewClickButton = 2; // prepare cancel click
                        while(ZeeConfig.lastInvItemMs < t1 && !ZeeConfig.isCancelClick()){
                            sleep(PING_MS);
                        }
                        //timeout?
                        if (ZeeConfig.isCancelClick()){
                            println("label plant canceled by click");
                            clickedPlantGobForLabelingQl = false;
                            ZeeConfig.removePlayerText();
                            return;
                        }
                        // scythe not allowed
                        if (ZeeManagerItemClick.isItemEquipped("/scythe")){
                            println("labelHarvestedPlant > cancel labeling due to scythe equipped");
                            clickedPlantGobForLabelingQl = false;
                            ZeeConfig.removePlayerText();
                            return;
                        }
                        //label plant
                        Inventory inv = ZeeConfig.getMainInventory();
                        ZeeConfig.addGobText(
                            ZeeConfig.lastMapViewClickGob,
                            Inventory.getQualityInt(ZeeConfig.lastInvItem).toString()
                        );
                    }
                    else{
                        // idle pose failed, cancel click?
                        println("labelHarvestedPlant > failed waiting player idle pose");
                        clickedPlantGobForLabelingQl = false;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                clickedPlantGobForLabelingQl = false;
                ZeeConfig.removePlayerText();
            }
        }.start();

    }

    static boolean activateHarvestGob(Gob cropGob){
        if (cropGob==null)
            return false;
        gobClick(cropGob, 3, UI.MOD_SHIFT);
        return waitCursorName(ZeeConfig.CURSOR_HARVEST);
    }

    static boolean quickFarmSelection = false;
    static void checkRightClickGob(Coord pc, Coord2d mc, Gob gob, String gobName) {

        startRightClickZooming(gob, pc);

        //long click starts harvest selection
        if(isGobHarvestable(gobName)){
            new ZeeThread(){
                public void run() {
                    try {
                        //TODO waitMouseUp
                        long startMs = lastClickMouseDownMs;
                        while(now() - startMs < LONG_CLICK_MS) {
                            sleep(50);
                        }
                        //mouse still down
                        if(lastClickMouseUpMs < startMs){
                            sleep(PING_MS);
                            if (getFlowerMenu()!=null){
                                ZeeConfig.cancelFlowerMenu();
                                waitNoFlowerMenu();
                            }
                            quickFarmSelection = true;
                            if (activateHarvestGob(gob)) {
                                //creates new MapView.Selector
                                ZeeConfig.gameUI.map.uimsg("sel", 1);
                                sleep(555);
                                boolean ret = ZeeConfig.gameUI.map.selection.mmousedown(ZeeConfig.getGobTile(gob),1);
                            }else{
                                println("coundt activate harvest gob");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    quickFarmSelection = false;
                }
            }.start();
        }

        // bugCollectionAuto label bug containers and wood pile
        if (ZeeManagerCraft.bugColRecipeOpen){
            ZeeManagerCraft.bugColGobClicked(gob);
        }

        // label harvested plant ql
        clickedPlantGobForLabelingQl = gobName.endsWith("plants/wine") || gobName.endsWith("plants/hops") || gobName.endsWith("plants/pepper");
        if(clickedPlantGobForLabelingQl) {
            if (ZeeManagerItemClick.isItemInHandSlot("/scythe")) {
                //println("cancel labeling plant > scythe already equipped");
                clickedPlantGobForLabelingQl = false;
            }
            else if(ZeeManagerItemClick.isTwoHandedItemEquippable("/scythe")){
                //println("cancel labeling plant > scythe equippable");
                clickedPlantGobForLabelingQl = false;
            }
        }

        // click barrel transfer
        if (gobName.endsWith("/barrel") && ZeeConfig.getPlayerPoses().contains(ZeeConfig.POSE_PLAYER_LIFTING)) {
            new ZeeThread() {
                public void run() {
                    try {
                        if(!waitPlayerDistToGob(gob,15))
                            return;
                        sleep(555);
                        String barrelName = ZeeConfig.getBarrelOverlayBasename(gob);
                        if (!barrelName.isEmpty())
                            ZeeConfig.addGobText(gob, barrelName);
                        Gob carryingBarrel = ZeeConfig.isPlayerLiftingGob("/barrel");
                        if (carryingBarrel!=null) {
                            barrelName = ZeeConfig.getBarrelOverlayBasename(carryingBarrel);
                            if (!barrelName.isEmpty())
                                ZeeConfig.addGobText(carryingBarrel, barrelName);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
        // clicked wheelbarrel
        else if(gobName.endsWith("/wheelbarrow")  && !ZeeConfig.isPlayerLiftingGob(gob)){
            new ZeeThread() {
                public void run() {
                    try {
                        if (ZeeConfig.isPlayerMountingHorse()) {
                            //dismount horse
                            dismountHorse(mc);
                            //re-drive wheelbarrow
                            gobClick(gob,3);
                        }
                        if (ZeeConfig.isPlayerDrivingingKicksled()) {
                            //disembark kicksled
                            disembarkVehicle(mc);
                            //re-drive wheelbarrow
                            gobClick(gob,3);
                        }
                        //show gridline
                        if(ZeeConfig.autoToggleGridLines && waitPlayerPose(ZeeConfig.POSE_PLAYER_DRIVE_WHEELBARROW)){
                            ZeeConfig.gameUI.map.showgrid(true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
        // while driving wheelbarrow: lift and click
        else if (ZeeConfig.isPlayerDrivingWheelbarrow() &&
                ( isGobInListEndsWith(gobName,"/cart,/rowboat,/snekkja,/knarr,/wagon,/spark,/gardenshed,/upstairs,/downstairs,/cellardoor,/cellarstairs,/minehole,/ladder,/cavein,/caveout,/burrow,/igloo,gate")
                  || isGobHouse(gobName) || isGobHouseInnerDoor(gobName)))
        {
            new ZeeThread() {
                public void run() {
                    Gob wb = ZeeConfig.getClosestGobByNameContains("/wheelbarrow");
                    if (isGobHouse(gobName)) {
                        try {
                            liftGob(wb);
                            sleep(100);
                            gobClick(gob, 3, 0, 16);//gob's door?
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else{
                        liftGobAndClickTarget(wb, gob);
                    }
                }
            }.start();
        }
        // gob requires unmounting horse/kicksled (if not lifting gob itself)
        else if (isGobRequireDisembarkVehicle(gob) && !ZeeConfig.isPlayerLiftingGob(gob)){
            // unmount horse
            if (ZeeConfig.isPlayerMountingHorse() && !ZeeManagerGobClick.isGobInListEndsWith(gobName,"/ladder,/minehole") && ZeeConfig.getMainInventory().countItemsByNameContains("/rope") > 0) {
                new ZeeThread() {
                    public void run() {
                        if(dismountHorse(mc)) {
                            // entering a house
                            if (isGobHouse(gobName)) {
                                gobClick(gob, 3, 0, 16);//gob's door?
                            }
                            // entering a non-house (cave, mine, cellar, ladder)
                            else {
                                gobClick(gob, 3);
                            }
                            //  schedule auto remount
                            if (isGobAmbientPassage(gob) && !ZeeConfig.playerHasAnyPose(ZeeConfig.POSE_PLAYER_LIFTING)) {
                                ZeeManagerGobClick.remountClosestHorse = true;
                            }
                        }
                    }
                }.start();
            }
            // disembark kicksled
            else if(ZeeConfig.isPlayerDrivingingKicksled()){
                new ZeeThread() {
                    public void run() {
                        try {
                            disembarkVehicle(mc);
                            if(waitPlayerPoseNotInListTimeout(1000,ZeeConfig.POSE_PLAYER_KICKSLED_IDLE, ZeeConfig.POSE_PLAYER_KICKSLED_ACTIVE)) {
                                sleep(100);//lagalagalaga
                                if (isGobHouse(gobName))
                                    gobClick(gob, 3, 0, 16);//gob's door?
                                else
                                    gobClick(gob, 3);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        }
        // use wheelbarrow on stockpile, dismount if necessary
        else if ( isGobStockpile(gobName) && ZeeConfig.isPlayerCarryingWheelbarrow()){
            new ZeeThread() {
                public void run() {
                    unloadWheelbarrowAtGob(gob);
                    if (ZeeConfig.autoToggleGridLines)
                        ZeeConfig.gameUI.map.showgrid(true);
                }
            }.start();
        }
        // mount horse while carrying/driving wheelbarrow
        else if ( isGobHorse(gobName) && (ZeeConfig.isPlayerCarryingWheelbarrow() || ZeeConfig.isPlayerDrivingWheelbarrow())){
            new ZeeThread() {
                public void run() {
                    if (ZeeConfig.isPlayerMountingHorse())
                        dismountHorse(mc);//horse to horse?
                    if (ZeeConfig.isPlayerDrivingWheelbarrow())
                        mountHorseDrivingWheelbarrow(gob);
                    else
                        mountHorseCarryingWheelbarrow(gob);
                }
            }.start();
        }

    }

    static boolean isRightClickZooming = false;
    static int rClickZoomLastY;
    private static void startRightClickZooming(Gob g, Coord pc) {
        rClickZoomLastY = pc.y;
        // start watching if clicked center box of 50x50
        //if ( Math.abs(rcguisz.x - pc.x) < 50  &&  Math.abs(rcguisz.y - pc.y) < 50){
        if(g.id == ZeeConfig.gameUI.plid){//clicked player?
            isRightClickZooming = true;
        }else{
            isRightClickZooming = false;
        }
    }
    static void rightClickZoom(Coord pc){
        if(pc.y < rClickZoomLastY){
            ZeeConfig.gameUI.map.camera.wheel(pc,-2);
        }
        else if(pc.y > rClickZoomLastY){
            ZeeConfig.gameUI.map.camera.wheel(pc,2);
        }
        rClickZoomLastY = pc.y;
    }

    static boolean isGobAmbientPassage(Gob gob){
        String gobName = gob.getres().name;
        if( isGobHouseInnerDoor(gobName) ||
            isGobHouse(gobName) ||
            isGobInListEndsWith(gobName,"/upstairs,/downstairs,/minehole,"+
                    "/ladder,/cavein,/caveout,/burrow,/igloo,/cellardoor") )
        {
            return true;
        }

        return false;
    }

    static boolean isGobRequireDisembarkVehicle(Gob gob) {

        String gobName = gob.getres().name;

        if ( gobName.endsWith("/cellardoor") && ZeeConfig.distanceToPlayer(gob) > TILE_SIZE*1 ){
            return true;
        }

        if (isGobAmbientPassage(gob) && !gobName.endsWith("/cellardoor")){
            return true;
        }

        if( isGobSittingFurniture(gobName)) {
            return true;
        }

        if(isGobInListEndsWith(gobName,"/wheelbarrow,/loom,/churn,/swheel,/ropewalk,/meatgrinder,/potterswheel,/quern,/plow,/winepress,/hookah")){
            return true;
        }

        // avoid dismouting when transfering to cauldron
        if (gobName.contains("cauldron") && !ZeeConfig.isPlayerLiftingGob()) {
            return true;
        }

        return false;
    }

    static boolean isGobSittingFurniture(String gobName) {
        if ( gobName.contains("/furn/") &&
            isGobInListContains(gobName,"throne,chair,sofa,stool,bench") )
            return true;
        if ( gobName.contains("rockinghorse") )
            return true;
        return false;
    }

    public static boolean isGobHouseInnerDoor(String gobName){
        return gobName.endsWith("-door");
    }

    public static boolean isGobHouse(String gobName) {
        String list = "/logcabin,/timberhouse,/stonestead,/stonemansion,/stonetower,/greathall,/windmill,/greenhouse,/igloo,/primitivetent";
        return isGobInListEndsWith(gobName,list);
    }

    private static void scheduleDestroyTreelog(Gob treelog) {
        if (treelogsForDestruction==null) {
            treelogsForDestruction = new ArrayList<Gob>();
        }

        if (treelogsForDestruction.contains(treelog)) {
            // remove treelog from queue
            removeScheduledTreelog(treelog);
        } else if (!currentDestroyingTreelog.equals(treelog)){
            // add treelog to queue
            treelogsForDestruction.add(treelog);
            ZeeConfig.addGobText(treelog,"des "+treelogsForDestruction.size());
        }
    }

    private static Gob removeScheduledTreelog(Gob treelog) {
        // remove treelog from queue
        treelogsForDestruction.remove(treelog);
        ZeeConfig.removeGobText(treelog);
        // update queue gob's texts
        for (int i = 0; i < treelogsForDestruction.size(); i++) {
            ZeeConfig.addGobText(treelogsForDestruction.get(i),"des "+(i+1));
        }
        return treelog;
    }


    private static void scheduleRemoveTree(Gob tree) {
        if (treesForRemoval==null) {
            treesForRemoval = new ArrayList<Gob>();
        }

        if (treesForRemoval.contains(tree)) {
            // remove tree from queue
            removeScheduledTree(tree);
        }
        else if (currentRemovingTree!=null && !currentRemovingTree.equals(tree)){
            // add tree to queue
            treesForRemoval.add(tree);
            ZeeConfig.addGobText(tree,"rem "+treesForRemoval.size());
        }
    }

    private static Gob removeScheduledTree(Gob tree) {
        // remove tree from queue
        ZeeConfig.removeGobText(tree);
        treesForRemoval.remove(tree);
        // update queue gob's texts
        for (int i = 0; i < treesForRemoval.size(); i++) {
            ZeeConfig.addGobText(treesForRemoval.get(i),"rem"+(i+1));
        }
        return tree;
    }

    private static void toggleOverlayAggro(Gob gob) {
        Gob.Overlay ol = gob.findol(OVERLAY_ID_AGGRO);
        if (ol!=null) {
            //remove all aggro radius
            ZeeConfig.findGobsByNameStartsWith("gfx/kritter/").forEach(gob1 -> {
                if (ZeeConfig.isAggressive(gob1.getres().name)) {
                    Gob.Overlay ol1 = gob1.findol(OVERLAY_ID_AGGRO);
                    if (ol1!=null)
                        ol1.remove();
                }
            });
        }
        else if (ZeeConfig.aggroRadiusTiles > 0) {
            //add all aggro radius
            ZeeConfig.findGobsByNameStartsWith("gfx/kritter/").forEach(gob1 -> {
                if (ZeeConfig.isAggressive(gob1.getres().name)) {
                    gob1.addol(new Gob.Overlay(gob1, new ZeeGobRadius(gob1, null, ZeeConfig.aggroRadiusTiles * MCache.tilesz2.y), ZeeManagerGobClick.OVERLAY_ID_AGGRO));
                }
            });
        }
    }

    private static void unloadWheelbarrowAtGob(Gob gob) {
        ZeeManagerStockpile.useWheelbarrowAtStockpile(gob);
    }

    public static void disembarkVehicle(Coord coordMc) {
        ZeeConfig.clickCoord(coordMc,1,UI.MOD_CTRL);
    }

    public static void disembarkVehicle(Coord2d coordMc) {
        disembarkVehicle(coordMc.floor(posres));
    }

    public static boolean dismountHorse(Coord2d coordMc) {
        Gob horse = ZeeConfig.getClosestGobByNameContains("gfx/kritter/horse/");
        ZeeConfig.clickCoord(coordMc.floor(posres),1,UI.MOD_CTRL);
        if(waitPlayerDismounted(horse)) {
            if (ZeeConfig.autoRunLogin && !ZeeConfig.isPlayerMountingHorse() && ZeeConfig.getPlayerSpeed() != ZeeConfig.PLAYER_SPEED_RUN) {
                ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_RUN);
            }
            return true;
        }else{
            return false;
        }
    }

    public static void mountHorse(Gob horse){
        int playerSpeed = ZeeConfig.getPlayerSpeed();
        clickGobPetal(horse,"Giddyup!");
        waitPlayerMounted(horse);
        if (ZeeConfig.autoRunLogin && ZeeConfig.isPlayerMountingHorse() && ZeeConfig.getPlayerSpeed() != ZeeConfig.PLAYER_SPEED_RUN) {
            ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_RUN);
        }
    }

    private static void clickedGobHoldingItem(Gob gob, String gobName) {
        if (isGobStockpile(gobName))
            itemActGob(gob,UI.MOD_SHIFT);//try piling all items
        else
            gobClick(gob,3,0); // try ctrl+click simulation
    }

    private static void pickAllDreamsCloseBy(Gob catcher1){
        new Thread(){
            public void run() {

                try{
                    ZeeConfig.addPlayerText("dreaming");

                    //prepare for clickCancelTask()
                    ZeeConfig.lastMapViewClickButton = 2;

                    //pick dreams from 1st catcher
                    pickDreamsFromCatcher(catcher1);
                    sleep(100);

                    //try picking from other catchers closeby
                    List<Gob> catchers = ZeeConfig.findGobsByNameEndsWith("/dreca");
                    catchers.removeIf(dreca -> ZeeConfig.distanceToPlayer(dreca) > 40);
                    Gob dc;
                    for (int i = 0; i < catchers.size(); i++) {
                        dc = catchers.get(i);
                        //cancel click
                        if (ZeeConfig.isCancelClick())
                            break;
                        //skip 1st catcher
                        if(dc.equals(catcher1))
                            continue;
                        pickDreamsFromCatcher(dc);
                        sleep(100);
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }

                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    private static void pickDreamsFromCatcher(Gob dreamCatcher) {
        ZeeConfig.addGobText(dreamCatcher,"target");
        if(clickGobPetal(dreamCatcher,"Harvest")) {
            waitPlayerDistToGob(dreamCatcher,15);
            waitNoFlowerMenu();
            if(clickGobPetal(dreamCatcher,"Harvest"))
                waitNoFlowerMenu();
        }
        ZeeConfig.removeGobText(dreamCatcher);
    }

    public static boolean pickupTorch() {
        if (ZeeManagerItemClick.pickupBeltItem("/torch")) {
            return true;
        }else if(ZeeManagerItemClick.pickupHandItem("/torch")){
            return true;
        }else if (ZeeManagerItemClick.pickUpInvItem(ZeeConfig.getMainInventory(),"/torch")){
            return true;
        }
        return false;
    }


    public static void groundZeeMenuClicked(Coord2d coordMc, String petalName){

        if (petalName.contentEquals("dig"))
            ZeeConfig.gameUI.menu.wdgmsg("act","dig","0");
        else if (petalName.contentEquals("mine"))
            ZeeConfig.gameUI.menu.wdgmsg("act","mine","0");
        else if (petalName.contentEquals("survey"))
            ZeeConfig.gameUI.menu.wdgmsg("act","survey","0");
        else if (petalName.contentEquals("fish"))
            ZeeConfig.gameUI.menu.wdgmsg("act","fish","0");
        else if (petalName.contentEquals("inspect cup"))
            inspectWaterAt(coordMc);
        else if(petalName.contentEquals("embark coracle"))
            dropEmbarkCoracle(coordMc);
        else if(petalName.contentEquals( "build road"))
            ZeeConfig.gameUI.menu.wdgmsg("act","bp","woodendstone","0");
    }

    public static void gobZeeMenuClicked(Gob gob, String petalName){

        String gobName = gob.getres().name;

        if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_OPENCATTLEROSTER))
            ZeeConfig.gameUI.menu.wdgmsg("act","croster");
        else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_MEMORIZEAREANIMALS))
            ZeeConfig.gameUI.menu.wdgmsg("act","croster","a");
        else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_TILEMONITOR))
            ZeeManagerMiner.tileMonitorWindow();
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_SWITCHCHAR))
            ZeeSess.charSwitchCreateWindow();
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_TESTCOORDS))
            windowTestCoords();
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_CLEARGOBTEXTS))
            clearAllGobsTexts();
        else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_AUTOBUTCH_BIGDEADANIMAL)){
            autoButchBigDeadAnimal(gob);
        }
        else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_LIFTUPGOB)){
            liftGob(gob);
        }
        else if(gobName.endsWith("terobjs/oven")) {
            addFuelGobMenu(gob,petalName);
        }
        else if(gobName.endsWith("terobjs/smelter")){
            addFuelGobMenu(gob,petalName);
        }
        else if (isGobTrellisPlant(gobName)){
            if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_REMOVEPLANT)) {
                destroyGob(gob);
            }
            else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_REMOVEALLPLANTS)){
                removeAllTrellisPlants(gob);
            }
            else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_CURSORHARVEST)){
                if (!ZeeConfig.getCursorName().equals(ZeeConfig.CURSOR_HARVEST))
                    gobClick(gob, 3, UI.MOD_SHIFT);
            }
        }
        else if(isGobTree(gobName)){
            if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_REMOVETREEANDSTUMP)
                || petalName.contentEquals(ZeeFlowerMenu.STRPETAL_REMOVEALLTREES))
            {
                removeTreeAndStump(gob, petalName);
            }
            else if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_INSPECT)) {//towercap case
                inspectGob(gob);
            }
        }
        else if (isGobCrop(gobName)) {
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_SEEDFARMER)) {
                ZeeManagerFarmer.showWindow(gob);
            }
            else if (petalName.equals(ZeeFlowerMenu.STRPETAL_CURSORHARVEST)) {
                if (!ZeeConfig.getCursorName().equals(ZeeConfig.CURSOR_HARVEST))
                    gobClick(gob, 3, UI.MOD_SHIFT);
            }
        }
        else if (petalName.equals(ZeeFlowerMenu.STRPETAL_BARRELTAKEALL)) {
            barrelTakeAllSeeds(gob);
        }
        else if ( petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3)
            || petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG5)
            || petalName.contentEquals(ZeeFlowerMenu.STRPETAL_DESTROYALL))
        {
            destroyTreelogs(gob,petalName);
        }
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_BUILD_PYRE)){
            ZeeConfig.gameUI.menu.wdgmsg("act","bp","bpyre",0);
        }
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_CRAFT_FIREBRAND)){
            ZeeConfig.gameUI.menu.wdgmsg("act","craft","firebrand",0);
        }
        else if(petalName.contentEquals(ZeeFlowerMenu.STRPETAL_CRAFT_PYRITE)){
            ZeeConfig.gameUI.menu.wdgmsg("act","craft","pyritespark",0);
        }
        else if(petalName.contentEquals("wave")){
            ZeeConfig.gameUI.menu.wdgmsg("act","pose","wave",0);
        }
        else if(petalName.contentEquals("laugh")){
            ZeeConfig.gameUI.menu.wdgmsg("act","pose","lol",0);
        }
        else{
            println("chooseGobFlowerMenu > unkown case");
        }
    }

    static void clearAllGobsTexts() {
        try {
            synchronized (ZeeConfig.gameUI.ui.sess.glob.oc) {
                ZeeConfig.gameUI.ui.sess.glob.oc.forEach(gob -> {
                    synchronized (gob) {
                        Gob.Overlay ol = gob.findol(ZeeGobText.class);
                        if (ol != null) {
                            ol.remove(false);
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void autoButchBigDeadAnimal(Gob deadAnimal) {
        new ZeeThread() {
            public void run() {
                boolean butcherBackup = ZeeConfig.butcherMode;
                ZeeConfig.butcherAutoList = ZeeConfig.DEF_LIST_BUTCH_AUTO;
                try{
                    ZeeConfig.addPlayerText("autobutch");
                    ZeeConfig.lastMapViewClickButton = 2;//prepare for clickCancelTask()
                    while (!ZeeConfig.isCancelClick() && gobExistsBecauseFlowermenu(deadAnimal)) {

                        //prepare settings
                        ZeeConfig.lastInvItemMs = 0;
                        ZeeConfig.butcherMode = true;
                        ZeeConfig.autoClickMenuOption = false;

                        //click gob
                        gobClick(deadAnimal,3);

                        //wait not butching
                        waitNotPlayerPose(ZeeConfig.POSE_PLAYER_BUTCH);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
                ZeeConfig.butcherMode = butcherBackup;
                ZeeConfig.autoClickMenuOption = Utils.getprefb("autoClickMenuOption", true);
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    private static void destroyTreelogs(Gob firstTreelog, String petalName) {
        if (!ZeeManagerItemClick.isItemInHandSlot("/bonesaw") || ZeeManagerItemClick.isItemInHandSlot("/saw-m")){
            ZeeConfig.msg("need bone saw equipped, no metal saw");
            return;
        }
        Gob treelog = firstTreelog;
        int logs = 2;
        try {
            waitNoFlowerMenu();
            String treelogName = treelog.getres().name;
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3)) {
                logs = 3;
            } else if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG5)) {
                logs = 5;
            } else if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYALL)) {
                isDestroyingAllTreelogs = true;
                logs = 999;
            }
            ZeeConfig.destroyingTreelogs = true;
            ZeeConfig.lastMapViewClickButton = 2;//prepare for cancel click
            while ( logs > 0  &&  !ZeeConfig.isCancelClick() ) {
                ZeeConfig.addPlayerText("treelogs "+logs);
                if (!clickGobPetal(treelog,"Make boards")){
                    println("can't click treelog = "+treelog);
                    logs = -1;
                    currentDestroyingTreelog = null;
                    continue;
                }
                currentDestroyingTreelog = treelog;
                waitPlayerIdlePose();
                if (!ZeeConfig.isCancelClick()){
                    logs--;
                    if (isDestroyingAllTreelogs){
                        // destroy all, treelog queue is present
                        if (treelogsForDestruction != null) {
                            if (treelogsForDestruction.size() > 0) {
                                treelog = removeScheduledTreelog(treelogsForDestruction.remove(0));
                            } else {
                                //stop destroying when queue consumed
                                println("logs -1, treelogsForDestruction empty");
                                logs = -1;
                            }
                        }else{
                            // destroy all, no treelog queue
                            treelog = getClosestTreeLog();
                        }
                    } else {
                        // destroy 3 or 5 same type treelogs
                        treelog = ZeeConfig.getClosestGobByNameContains(treelogName);
                    }
                }else{
                    if (ZeeConfig.isCancelClick()) {
                        ZeeConfig.msg("destroy treelog canceled by click");
                        println("destroy treelog canceled by click");
                    }else
                        println("destreelog canceled by gobHasFlowermenu?");
                    logs = -1;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        isDestroyingAllTreelogs = false;
        ZeeConfig.destroyingTreelogs = false;
        currentDestroyingTreelog = null;
        if (treelogsForDestruction!=null)
            treelogsForDestruction.clear();
        treelogsForDestruction = null;
        ZeeConfig.removePlayerText();
    }

    public static Gob getClosestTree() {
        List<Gob> list = ZeeConfig.findGobsByNameContains("/trees/");
        list.removeIf(gob1 -> !isGobTree(gob1.getres().name));
        return ZeeConfig.getClosestGob(list);
    }

    public static Gob getClosestTreeLog() {
        List<Gob> list = ZeeConfig.findGobsByNameContains("/trees/");
        list.removeIf(gob1 -> !isGobTreeLog(gob1.getres().name));
        return ZeeConfig.getClosestGob(list);
    }

    private static boolean showGobFlowerMenu(){

        boolean showMenu = true;
        ZeeFlowerMenu menu = null;
        ArrayList<String> opts;//petals array

        if (isGroundClick) {
            if (isWaterTile(coordMc)) {
                boolean isShallowWater = ZeeConfig.isTileNamed(coordMc,ZeeConfig.TILE_WATER_FRESH_SHALLOW,ZeeConfig.TILE_WATER_OCEAN_SHALLOW);
                opts = new ArrayList<String>();
                if (isShallowWater)
                    opts.add("dig");
                opts.add("fish");
                if (isShallowWater)
                    opts.add("build road");
                opts.add("inspect cup");
                if (ZeeManagerItemClick.isCoracleEquipped() && !ZeeConfig.isPlayerMountingHorse()) {
                    opts.add("embark coracle");
                }
                menu = new ZeeFlowerMenu(coordMc, opts.toArray(String[]::new));
            }
            else{
                menu = new ZeeFlowerMenu(coordMc, "dig", "mine");
            }
        }
        else if(gob.tags.contains(Gob.Tag.PLAYER_MAIN)) {
            opts = new ArrayList<String>();
            opts.add(ZeeFlowerMenu.STRPETAL_SWITCHCHAR);
            opts.add(ZeeFlowerMenu.STRPETAL_CLEARGOBTEXTS);
            opts.add(ZeeFlowerMenu.STRPETAL_TESTCOORDS);
            opts.add("wave");
            opts.add("laugh");
            if (ZeeConfig.isCaveTile(ZeeConfig.getPlayerTileName()))
                opts.add(ZeeFlowerMenu.STRPETAL_TILEMONITOR);
            menu = new ZeeFlowerMenu(gob, opts.toArray(String[]::new));
        }
        else if (isGobTamedAnimal(gobName) && !isGobDeadOrKO(gob)) {
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_OPENCATTLEROSTER, ZeeFlowerMenu.STRPETAL_MEMORIZEAREANIMALS);
        }
        else if (isGobButchable(gobName) && isGobDeadOrKO(gob)) {
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_AUTOBUTCH_BIGDEADANIMAL, ZeeFlowerMenu.STRPETAL_LIFTUPGOB);
        }
        else if(gobName.endsWith("terobjs/oven")){
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_ADD4BRANCH);
        }
        else if(gobName.endsWith("terobjs/smelter")){
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_ADD9COAL, ZeeFlowerMenu.STRPETAL_ADD12COAL);
        }
        else if (isGobTrellisPlant(gobName)){
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_REMOVEPLANT, ZeeFlowerMenu.STRPETAL_REMOVEALLPLANTS,ZeeFlowerMenu.STRPETAL_CURSORHARVEST);
        }
        else if (isGobTree(gobName)){
            opts = new ArrayList<String>();
            opts.add(ZeeFlowerMenu.STRPETAL_REMOVETREEANDSTUMP);
            opts.add(ZeeFlowerMenu.STRPETAL_REMOVEALLTREES);
            if (gobName.endsWith("/towercap"))
                opts.add(ZeeFlowerMenu.STRPETAL_INSPECT);
            menu = new ZeeFlowerMenu(gob, opts.toArray(String[]::new));
        }
        else if (isGobCrop(gobName)) {
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_SEEDFARMER, ZeeFlowerMenu.STRPETAL_CURSORHARVEST);
        }
        else if (isBarrelTakeAll(gob)) {
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_BARRELTAKEALL, ZeeFlowerMenu.STRPETAL_LIFTUPGOB);
        }
        else if (isDestroyTreelog()) {
            menu = new ZeeFlowerMenu( gob, ZeeFlowerMenu.STRPETAL_LIFTUPGOB,
                ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3,
                ZeeFlowerMenu.STRPETAL_DESTROYTREELOG5,
                ZeeFlowerMenu.STRPETAL_DESTROYALL
            );
        }
        else if (gobName.endsWith("/wildbeehive")) {
            menu = new ZeeFlowerMenu( gob, ZeeFlowerMenu.STRPETAL_BUILD_PYRE);
        }
        else if (isGobFireTarget(gob)) {
            menu = new ZeeFlowerMenu( gob,
                ZeeFlowerMenu.STRPETAL_CRAFT_FIREBRAND,
                ZeeFlowerMenu.STRPETAL_CRAFT_PYRITE
            );
        }
        else{
            showMenu = false;
            //println("showGobFlowerMenu() > unkown case");
        }

        if (showMenu) {
            ZeeConfig.gameUI.ui.root.add(menu, coordPc);
        }

        return showMenu;
    }

    private static boolean isGobDeadAnimal;

    @SuppressWarnings("unchecked")
    public static void labelGobByContents(Window window) {
        new Thread(){
            public void run() {
                try{

                    sleep(250);//wait window build
                    if (window.children()==null) {
                        println("labelGobByContents > window.children null");
                        return;
                    }

                    window.children().forEach(w1 -> {
                        if (w1.getClass().getSimpleName().contentEquals("RelCont")){
                            w1.children().forEach(w2 -> {
                                if (w2.getClass().getSimpleName().contentEquals("TipLabel")){

                                    try {
                                        // get window info
                                        List<ItemInfo> info = (List<ItemInfo>) w2.getClass().getDeclaredField("info").get(w2);

                                        // get name
                                        String name = ZeeManagerItemClick.getItemInfoName(info);
                                        if(name==null || name.isBlank()) {
                                            // empty gob, remove text
                                            ZeeConfig.removeGobText(ZeeConfig.lastMapViewClickGob);
                                            return;
                                        }

                                        // (15.45) (l) of (Cave Slime)
                                        Pattern pattern = Pattern.compile("(\\d+\\.?+\\d+)\\s+(\\S+) of ([\\S\\s]+)$");//.compile("(.*?)(\\d+)(.*)");
                                        Matcher matcher = pattern.matcher(name);
                                        matcher.find();
                                        String quantity = String.format("%.0f",Math.rint(Double.parseDouble(matcher.group(1))));
                                        String metric = matcher.group(2);
                                        String substance = matcher.group(3).replaceAll("\\s+","");

                                        String gobText = quantity + metric + " " + substance;

                                        // get quality
                                        Double ql = ZeeManagerItemClick.getItemInfoQuality(info);
                                        if(ql > 0) {
                                            gobText += ql.intValue();
                                        }

                                        // label gob "name q"
                                        // TODO create variable lastClickedBarrelCIstern to avoid rare mislabeling
                                        ZeeConfig.addGobText(ZeeConfig.lastMapViewClickGob, gobText);

                                    } catch (IllegalAccessException e) {
                                        e.printStackTrace();
                                    } catch (NoSuchFieldException e) {
                                        e.printStackTrace();
                                    }
                                    return;
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    static void clickedMinimapGobicon(Gob gob, int btn) {
        try {
            if (ZeeConfig.clickIconStoatAggro && btn==3 && gob.getres().name.contains("/stoat")) {
                if(!ZeeConfig.isPlayerMountingHorse())
                    return;
                ZeeConfig.cursorChange(ZeeConfig.ACT_AGGRO);
                gobClick(gob, 1);
                ZeeConfig.clickRemoveCursor();
                ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_SPRINT);
                ZeeSynth.textToSpeakLinuxFestival("get");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    static void remountHorse() {
        ZeeManagerGobClick.remountClosestHorse = false;
        new ZeeThread(){
            public void run() {
                int countNotReady = 0;
                double backupAudio = Audio.volume;
                ZeeConfig.addPlayerText("mounting");
                try {
                    //wait horse gob loading
                    sleep(500);

                    //find horse
                    Gob closestHorse = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameEndsWith("/mare", "/stallion"));

                    //mute volume (msg method doesnt work)
                    Audio.setvolume(0);

                    //mount horse
                    ZeeManagerGobClick.clickGobPetal(closestHorse, "Giddyup!");
                    countNotReady = 0;//exit success?

                    // wait player mounting pose
                    waitPlayerPose(ZeeConfig.POSE_PLAYER_RIDING_IDLE);
                    sleep(500);

                } catch (Exception e) {
                    e.printStackTrace();
                }
                //restore volume (msg method doesnt work)
                Audio.setvolume(backupAudio);
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    static String gobVisNames;
    static boolean gobVisHitbox, gobVisHidden;
    static void windowGobHitboxAndVisibility() {
        Widget wdg;
        String title = "Gob visibility";

        Window win = ZeeConfig.getWindow(title);
        if (win != null){
            win.reqdestroy();
            win = null;
        }

        //create window
        win = ZeeConfig.gameUI.add(
                new Window(Coord.of(120,70),title){
                    public void wdgmsg(String msg, Object... args) {
                        if (msg.contentEquals("close")){
                            this.reqdestroy();
                        }
                    }
                },
                300,300
        );

        //label
        wdg = win.add(new Label("gob name contains (space sep.)"));

        //text entry
        wdg = win.add(new TextEntry(UI.scale(130),""){
            public void activate(String text) {
                // update barterstand labels
                barterSearchUpdateGobs();
            }
            public boolean keyup(KeyEvent ev) {
                gobVisNames = this.text();
                return true;
            }
        },0,wdg.c.y+wdg.sz.y);

        //checkbox hitbox
        wdg = win.add(new CheckBox("hitbox"){
            public void changed(boolean val) {
                gobVisHitbox = val;
                //barterSearchUpdateGobs();
            }
        },0,wdg.c.y+wdg.sz.y+5);

        //checkbox ore
        wdg = win.add(new CheckBox("hidden"){
            public void changed(boolean val) {
                gobVisHidden = val;
                //barterSearchUpdateGobs();
            }
        },wdg.c.x+wdg.sz.x+5,wdg.c.y);

        win.pack();
    }

    static boolean pickingIrrlight = false;
    public static void autoPickIrrlight() {
        if (pickingIrrlight) {
            // avoid being called multiple times by gob consumer
            //println("already picking irrlight");
            return;
        }
        pickingIrrlight = true;
        new ZeeThread(){
            public void run() {
                try {
                    // guess working station
                    Gob workingStation = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameEndsWith("/crucible","/anvil"));

                    // set max speed
                    ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_SPRINT);

                    // try picking irrlight
                    ZeeConfig.addPlayerText("irrlight!");
                    ZeeConfig.lastMapViewClickButton = 2;//prepare cancel click
                    while (pickingIrrlight && !ZeeConfig.isCancelClick()) {
                        Gob irrlight = ZeeConfig.getClosestGobByNameContains("/irrbloss");
                        if (irrlight==null) {
                            break;
                        }
                        gobClick(irrlight,3);
                        waitPlayerIdlePose();
                    }

                    //try crafting again
                    if (workingStation!=null && !ZeeConfig.isCancelClick()){
                        gobClick(workingStation,3);
                        waitPlayerIdlePose();
                        sleep(PING_MS);
                        ZeeConfig.getButtonNamed((Window) ZeeConfig.makeWindow.parent,"Craft All").click();
                        //drink while crafting
                        ZeeManagerItemClick.drinkFromBeltHandsInv();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                pickingIrrlight = false;
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    private boolean isGobBigDeadAnimal_thread() {
        try{
            ZeeThread zt = new ZeeThread() {
                public void run() {
                    gobClick(gob, 3);
                    if (!waitFlowerMenu()) {//no menu detected
                        isGobDeadAnimal = false;
                        return;
                    }
                    FlowerMenu fm = getFlowerMenu();
                    for (int i = 0; i < fm.opts.length; i++) {
                        //if animal gob has butch menu, means is dead
                        if (ZeeConfig.DEF_LIST_BUTCH_AUTO.contains(fm.opts[i].name)){
                            isGobDeadAnimal = true;
                            break;
                        }
                    }
                    //close menu before returning
                    ZeeConfig.cancelFlowerMenu();
                    waitNoFlowerMenu();
                }
            };

            //disable automenu settings before thread clicks gob
            ZeeConfig.autoClickMenuOption = false;
            boolean butchBackup = ZeeConfig.butcherMode;
            ZeeConfig.butcherMode = false;

            //start thread and wait it finish
            isGobDeadAnimal = false;
            zt.start();
            zt.join();//wait thread

            //restore automenu settings
            ZeeConfig.autoClickMenuOption = Utils.getprefb("autoClickMenuOption", true);
            ZeeConfig.butcherMode = butchBackup;

            return isGobDeadAnimal;

        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isDestroyTreelog() {
        if(isGobTreeLog(gobName) && ZeeManagerItemClick.isItemInHandSlot("bonesaw"))
            return true;
        return false;
    }

    private static void mountHorseDrivingWheelbarrow(Gob gob){
        Gob horse = gob;
        try{
            //waitNoFlowerMenu();
            ZeeConfig.addPlayerText("mounting");
            Gob wb = ZeeConfig.getClosestGobByNameContains("gfx/terobjs/vehicle/wheelbarrow");
            if (wb == null) {
                ZeeConfig.msg("no wheelbarrow close 1");
            } else {
                Coord pc = ZeeConfig.getPlayerCoord();
                Coord subc = ZeeConfig.getGobCoord(horse).sub(pc);
                int xsignal, ysignal;
                xsignal = subc.x >= 0 ? -1 : 1;//switch 1s to change direction relative to horse
                ysignal = subc.y >= 0 ? -1 : 1;
                //try position wheelbarrow away from horse direction
                ZeeConfig.clickCoord(pc.add(xsignal * 500, ysignal * 500), 1);
                sleep(PING_MS);
                gobClick(wb,3);//stop driving wheelbarrow
                sleep(PING_MS);
                mountHorse(horse);
                waitPlayerMounted(horse);
                liftGob(wb);// lift wheelbarrow
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private static void mountHorseCarryingWheelbarrow(Gob gob) {
        Gob horse = gob;
        try {
            //waitNoFlowerMenu();
            ZeeConfig.addPlayerText("mounting");
            Gob wb = ZeeConfig.getClosestGobByNameContains("gfx/terobjs/vehicle/wheelbarrow");
            if (wb == null) {
                ZeeConfig.msg("no wheelbarrow close 2");
            } else {
                Coord pc = ZeeConfig.getPlayerCoord();
                Coord subc = ZeeConfig.getGobCoord(horse).sub(pc);
                int xsignal, ysignal;
                xsignal = subc.x >= 0 ? -1 : 1;
                ysignal = subc.y >= 0 ? -1 : 1;
                //try to drop wheelbarrow away from horse direction
                ZeeConfig.clickCoord(pc.add(xsignal * 500, ysignal * 500), 3);
                sleep(500);
                //if drop wb success
                if (!ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    ZeeConfig.clickRemoveCursor();//remove hand cursor
                    mountHorse(horse);
                    waitPlayerMounted(horse);
                    liftGob(wb);//lift wheelbarrow
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private static void liftGobAndClickTarget(Gob liftGob, Gob target){
        try {
            waitNoFlowerMenu();
            ZeeConfig.addPlayerText("lift and click");
            double dist;
            //remove hand cursor
            ZeeConfig.clickRemoveCursor();
            liftGob(liftGob);
            dist = ZeeConfig.distanceToPlayer(liftGob);
            if (dist==0) {
                // click target
                gobClick(target, 3);
                //waitPlayerIdleVelocity();
            }else{
                ZeeConfig.msg("couldnt lift gob?");//impossible case?
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private static void openGateWheelbarrow(Gob gob) {
        // gfx/terobjs/vehicle/wheelbarrow
        Gob gate = gob;
        try {
            waitNoFlowerMenu();
            ZeeConfig.addPlayerText("wheeling");
            Gob wb = ZeeConfig.getClosestGobByNameContains("gfx/terobjs/vehicle/wheelbarrow");
            if (wb==null){
                ZeeConfig.msg("no wheelbarrow close 4");
            }else {
                double dist;
                liftGob(wb);
                sleep(PING_MS);
                dist = ZeeConfig.distanceToPlayer(wb);
                if (dist==0) {//lifted wb
                    gobClick(gate, 3);
                    waitPlayerIdleVelocity();
                }else{
                    //impossible case?
                    ZeeConfig.msg("wheelbarrow unreachable?");
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    public static boolean isGobGate(String gobName) {
        if (gobName.startsWith("gfx/terobjs/arch/") && gobName.endsWith("gate"))
            return true;
        return false;
    }


    // barrel is empty if has no overlays ("gfx/terobjs/barrel-flax")
    public static boolean isBarrelEmpty(Gob barrel){
        return ZeeManagerGobClick.getOverlayNames(barrel).isEmpty();
    }

    private static void removeAllTrellisPlants(Gob firstPlant) {
        Gob closestPlant = null;
        try{
            String gobName = firstPlant.getres().basename();
            ZeeConfig.addGobText(ZeeConfig.getPlayerGob(),"rem "+gobName);
            waitNoFlowerMenu();
            waitPlayerIdleFor(1);
            closestPlant = firstPlant;
            double dist;
            do{
                if (ZeeConfig.isCancelClick()) {
                    // cancel if clicked right/left button
                    println("cancel click");
                    break;
                }
                ZeeConfig.addGobText(closestPlant,"plant");
                destroyGob(closestPlant);
                if(!waitGobRemovedOrCancelClick(closestPlant))
                    break;
                closestPlant = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameContains(gobName));
                dist = ZeeConfig.distanceToPlayer(closestPlant);
                //println("dist "+dist);
            }while(dist < 25);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
        ZeeConfig.removeGobText(closestPlant);
    }

    static void removeTreeAndStump(Gob tree, String petalName){
        try{
            if (petalName.contentEquals(ZeeFlowerMenu.STRPETAL_REMOVEALLTREES)) {
                ZeeConfig.addPlayerText("rem trees");
                isRemovingAllTrees = true;
            }else {
                ZeeConfig.addPlayerText("rem tree&stump");
            }
            ZeeConfig.lastMapViewClickButton = 2;//prepare for cancel click
            if(!waitNoFlowerMenu()){
                println("remtree > failed waiting no flowemenu");
                exitRemoveAllTrees();
                return;
            }
            ZeeManagerItemClick.equipAxeChopTree();
            Coord2d treeCoord;
            while (tree!=null && !ZeeConfig.isCancelClick()) {
                sleep(500);//safe wait
                //start chopping
                if(!clickGobPetal(tree, "Chop")){
                    println("remtree > couldnt click tree petal \"Chop\"");
                    break;
                }
                sleep(500);//safe wait
                if(!waitPlayerPose(ZeeConfig.POSE_PLAYER_CHOPTREE)){
                    println("remtree > failed waiting pose choptree");
                    break;
                }
                currentRemovingTree = tree;
                treeCoord = new Coord2d(tree.rc.x, tree.rc.y);
                //wait idle
                if (!ZeeConfig.isCancelClick() && waitPlayerIdlePose()) {
                    //wait new stump loading
                    sleep(2000);
                    //check task canceled
                    if(ZeeConfig.isCancelClick()) {
                        println("remtree > click canceled");
                        break;
                    }
                    Gob stump = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameEndsWith("stump"));
                    if (stump != null) {
                        //stump location doesnt match tree and there's no other stump close
                        if (stump.rc.compareTo(treeCoord) != 0  &&  ZeeConfig.distanceToPlayer(stump) > 25){
                            println("remtree > stump undecided");
                            break;
                        }
                        ZeeConfig.addGobText(stump, "stump");
                        removeStumpMaybe(stump);
                        waitPlayerIdlePose();
                    } else {
                        println("remtree > stump is null");
                    }
                    if (isRemovingAllTrees) {
                        if (treesForRemoval!=null){
                            if (treesForRemoval.size()>0)
                                tree = removeScheduledTree(treesForRemoval.remove(0));
                            else
                                tree = null; // stop removing trees if queue was consumed
                        }else{
                            // remove all trees until player blocked or something
                            tree = getClosestTree();
                        }
                    }else {
                        tree = null;
                    }
                    //println("next tree = "+tree);
                }
                else
                    println("remtree > task canceled or !waitPlayerIdlePose");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        exitRemoveAllTrees();
    }

    private static void exitRemoveAllTrees() {
        isRemovingAllTrees = false;
        currentRemovingTree = null;
        if(treesForRemoval != null  &&  treesForRemoval.size() > 0) {
            ZeeConfig.removeGobText(treesForRemoval);
            //treesForRemoval.clear();
        }
        //treesForRemoval = null;
        ZeeConfig.removePlayerText();
    }

    public static boolean removeStumpMaybe(Gob stump) throws InterruptedException {
        boolean droppedBucket = false;

        //move closer to stump
        gobClick(stump,1);
        if(!waitPlayerDistToGob(stump,25)){
            println("couldn't get close to stump?");
            return false;
        }

        //drop bucket if present
        if (ZeeManagerItemClick.isItemEquipped("bucket")) {
            if (ZeeConfig.getStamina() < 100) {
                ZeeManagerItemClick.drinkFromBeltHandsInv();
                sleep(PING_MS*2);
                waitNotPlayerPose(ZeeConfig.POSE_PLAYER_DRINK);
            }
            ZeeManagerItemClick.getEquipory().dropItemByNameContains("/bucket");
            droppedBucket = true;
        }

        //equip shovel
        ZeeManagerItemClick.equipBeltItem("shovel");
        if (!waitItemInHand("shovel")){
            println("couldnt equip shovel ?");
            return false;
        }
        waitNotHoldingItem();//wait possible switched item go to belt?

        //remove stump
        destroyGob(stump);

        //reequip bucket if dropped
        if (droppedBucket){
            waitPlayerPose(ZeeConfig.POSE_PLAYER_IDLE);
            Gob bucket = ZeeConfig.getClosestGobByNameContains("/bucket");
            if (bucket!=null){
                if (ZeeManagerItemClick.pickupHandItem("shovel")) {
                    if(ZeeManagerItemClick.dropHoldingItemToBeltOrInv()) {
                        sleep(PING_MS);
                        ZeeConfig.clickRemoveCursor();
                        waitCursorName(ZeeConfig.CURSOR_ARW);
                        sleep(PING_MS);
                        gobClick(bucket, 3);
                        if (waitHoldingItem())
                            ZeeManagerItemClick.equipEmptyHand();
                        else
                            println("couldnt pickup da bucket");
                    }
                }else {
                    println("couldnt return shovel to belt?");
                }
            }else{
                println("bucket gob not found");
            }
        }

        // maybe stump was removed
        return true;
    }

    public static void addItemsToGob(List<WItem> invItens, int num, Gob gob){
        new ZeeThread(){
            public void run() {
                try{
                    if(invItens.size() < num){
                        ZeeConfig.msgError("Need "+num+" item(s)");
                        return;
                    }
                    boolean exit = false;
                    int added = 0;
                    ZeeConfig.lastMapViewClickButton = 2;//prepare for cancel click
                    ZeeConfig.addPlayerText("adding");
                    while(  !ZeeConfig.isCancelClick()
                            && !exit
                            && added < num
                            && invItens.size() > 0)
                    {
                        if(ZeeManagerItemClick.pickUpItem(invItens.get(0))){
                            itemActGob(gob,0);
                            if(waitNotHoldingItem()){
                                invItens.remove(0);
                                added++;
                            }else{
                                ZeeConfig.msgError("Couldn't right click "+gob.getres().basename());
                                exit = true;
                            }
                        }else {
                            ZeeConfig.msgError("Couldn't pickup inventory item");
                            exit = true;
                        }
                    }
                    ZeeConfig.addGobTextTempMs(gob,"Added "+added+" item(s)",3000);
                }catch (Exception e){
                    e.printStackTrace();
                }
                ZeeConfig.removePlayerText();
            }
        }.start();
    }

    public static void addFuelGobMenu(Gob gob, String petalName) {
        String gobName = gob.getres().name;
        if(gobName.endsWith("oven") && petalName.equals(ZeeFlowerMenu.STRPETAL_ADD4BRANCH)){
            /*
                fuel oven with 4 branches
             */
           List<WItem> branches = ZeeConfig.getMainInventory().getWItemsByNameContains("branch");
           if(branches.size() < 4){
               ZeeConfig.gameUI.msg("Need 4 branches to fuel oven");
               return;
           }
           boolean exit = false;
           int added = 0;
           while(!exit && added<4 && branches.size() > 0){
               if(ZeeManagerItemClick.pickUpItem(branches.get(0))){
                   itemActGob(gob,0);
                   if(waitNotHoldingItem()){
                       branches.remove(0);
                       added++;
                   }else{
                       ZeeConfig.gameUI.msg("Couldn't right click oven");
                       exit = true;
                   }
               }else {
                   ZeeConfig.gameUI.msg("Couldn't pickup branch");
                   exit = true;
               }
           }
           ZeeConfig.gameUI.msg("Added "+added+" branches");
        }
        else if(gobName.endsWith("smelter")){
            /*
                fuel smelter with 9 or 12 coal
             */
            int num = 12;
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_ADD9COAL))
                num = 9;
            final int numCoal = num;
            List<WItem> coal = ZeeConfig.getMainInventory().getWItemsByNameContains("coal");
            if(coal.size() < numCoal){
                ZeeConfig.gameUI.msg("Need "+numCoal+" coal to fuel smelter");
                return;
            }
            boolean exit = false;
            int added = 0;
            while(!exit && added<numCoal && coal.size() > 0){
                if(ZeeManagerItemClick.pickUpItem(coal.get(0))){
                    itemActGob(gob,0);
                    if(waitNotHoldingItem()){
                        coal.remove(0);
                        added++;
                    }else{
                        ZeeConfig.gameUI.msg("Couldn't right click smelter");
                        exit = true;
                    }
                }else {
                    ZeeConfig.gameUI.msg("Couldn't pickup coal");
                    exit = true;
                }
            }
            ZeeConfig.gameUI.msg("Added "+added+" coal");
        }
    }

    private boolean isFuelAction(String gobName) {
        if (gobName.endsWith("oven") || gobName.endsWith("smelter")){
            return true;
        }
        return false;
    }

    private static void harvestOneTrellis(Gob gob) {
        if(ZeeManagerItemClick.pickupBeltItem("scythe")){
            //hold scythe for user unequip it
        }else if(ZeeManagerItemClick.getLeftHandName().endsWith("scythe")){
            //hold scythe for user unequip it
            ZeeManagerItemClick.unequipLeftItem();
        }else{
            //no scythe around, just harvest
            clickGobPetal(gob,"Harvest");
        }
    }

    static boolean isAutoPressan = false;
    static void autoPressWine(Window window) {

        if (isAutoPressan) {
            println("already pressan");
            return;
        }
        isAutoPressan = true;

        new Thread(){

            public void run() {

                try {
                    //  "/grapes"  "seed-grape"
                    ZeeConfig.addPlayerText("pressan");
                    Button btnPress = ZeeConfig.getButtonNamed(window,"Press");
                    Inventory invPress = window.getchild(Inventory.class);
                    Inventory invPlayer = ZeeConfig.getMainInventory();
                    List<WItem> playerGrapes = invPlayer.getWItemsByNameEndsWith("/grapes");
                    List<WItem> pressGrapes = invPress.getWItemsByNameEndsWith("/grapes");
                    List<WItem> pressSeeds = invPress.getWItemsByNameEndsWith("/seed-grape");

                    if (pressGrapes.size()==0){
                        if (playerGrapes.size() > 0) {
                            playerGrapes.get(0).item.wdgmsg("transfer", Coord.z, -1);
                        }else{
                            exitAutoWinepress("no grapes to start pressing");
                            return;
                        }
                    }

                    //while idle pressing pose
                    while(ZeeConfig.getPlayerPoses().contains(ZeeConfig.POSE_PLAYER_PRESSINGWINE_IDLE)){

                        //start pressing
                        println("pressing");
                        btnPress.click();
                        sleep(PING_MS);

                        //wait stop pressing
                        waitPlayerPoseNotInList(ZeeConfig.POSE_PLAYER_PRESSINGWINE, ZeeConfig.POSE_PLAYER_DRINK);
                        sleep(PING_MS);

                        //exit if player left winepress
                        if (!ZeeConfig.getPlayerPoses().contains(ZeeConfig.POSE_PLAYER_PRESSINGWINE_IDLE)){
                            exitAutoWinepress("player left winepress");
                            return;
                        }

                        playerGrapes = invPlayer.getWItemsByNameEndsWith("/grapes");
                        pressGrapes = invPress.getWItemsByNameEndsWith("/grapes");
                        pressSeeds = invPress.getWItemsByNameEndsWith("/seed-grape");

                        if (pressGrapes.size() > 0){
                            exitAutoWinepress("press still has grapes, grapejuice full?");
                            return;
                        }

                        if(pressSeeds.size() > 0){
                            if (invPlayer.getNumberOfFreeSlots() == 0){
                                exitAutoWinepress("player inv full, cant switch winepress contents");
                                return;
                            }
                            println("press has only seeds, try refilling?");

                            //transfer seeds to player
                            pressSeeds.get(0).item.wdgmsg("transfer",Coord.z,-1);
                            sleep(PING_MS);

                            //transfer grapes to press
                            if (playerGrapes.size() == 0){
                                exitAutoWinepress("out of grapes to press");
                                return;
                            }
                            playerGrapes.get(0).item.wdgmsg("transfer",Coord.z,-1);

                            // restart pressing on next loop...
                        }
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }

                //exit
                exitAutoWinepress("ok");
            }

        }.start();

    }

    static void exitAutoWinepress(String msg) {
        println("exit autowine > "+msg);
        ZeeConfig.removePlayerText();
        isAutoPressan = false;
    }

    public static boolean isGobStockpile(String gobName) {
        return gobName.startsWith("gfx/terobjs/stockpile");
    }

    private static boolean isGobGroundItem(String gobName) {
        return gobName.startsWith("gfx/terobjs/items/");
    }

    public static boolean isLongMidClick() {
        return lastClickDiffMs >= LONG_CLICK_MS;
    }

    public static boolean isShortMidClick() {
        return lastClickDiffMs < LONG_CLICK_MS;
    }


    public static boolean isGobMineSupport(String gobName) {
        String list = "/minebeam,/column,/minesupport,/naturalminesupport,/towercap";
        return isGobInListEndsWith(gobName, list);
    }


    private static boolean isGobLiftable(String gobName) {
        if(isGobBoulder(gobName) || isGobSittingFurniture(gobName) || gobName.contains("/table-"))
            return true;
        String endList = "/meatgrinder,/potterswheel,/iconsign,/rowboat,/dugout,/wheelbarrow,"
                +"/compostbin,/gardenpot,/beehive,/htable,/bed-sturdy,/boughbed,/alchemiststable,"
                +"/gemwheel,/ancestralshrine,/spark,/cauldron,/churn,/wardrobe,"
                +"/trough,curdingtub,/plow,/barrel,/still,log,/oldtrunk,chest,/anvil,"
                +"/cupboard,/studydesk,/demijohn,/quern,/wreckingball-fold,/loom,/swheel,"
                +"/ttub,/cheeserack,/archerytarget,/dreca,/glasspaneframe,/runestone,"
                +"/foodtrough,woodbox,casket,basket,crate,chest";
        return isGobInListEndsWith(gobName,endList);
    }

    private static boolean isGobBoulder(String gobName) {
        return gobName.startsWith("gfx/terobjs/bumlings/") &&
               !gobName.startsWith("gfx/terobjs/bumlings/ras");
    }

    public static boolean isGobBush(String gobName) {
        return gobName.startsWith("gfx/terobjs/bushes");
    }

    public static boolean isGobTreeStump(String gobName) {
        return gobName.startsWith("gfx/terobjs/trees/") && gobName.endsWith("stump");
    }

    public static boolean isGobTree(String gobName) {
        return gobName.startsWith("gfx/terobjs/trees/") && !gobName.endsWith("log") && !gobName.endsWith("stump") && !gobName.endsWith("oldtrunk") && !gobName.startsWith("gfx/terobjs/trees/driftwood");
    }

    public static boolean isGobTreeLog(String gobName){
        return gobName.startsWith("gfx/terobjs/trees/") && gobName.endsWith("log");
    }

    public static boolean isBarrelTakeAll(Gob gob) {
        String gobName = gob.getres().name;
        if(!gobName.endsWith("barrel") || isBarrelEmpty(gob)){
            return false;
        }
        String list = "barley,carrot,cucumber,flax,grape,hemp,leek,lettuce,millet"
                +",pipeweed,poppy,pumpkin,wheat,turnip,wheat,barley,wheatflour,barleyflour,milletflour"
                +",ashes,gelatin,cavedust,caveslime,chitinpowder"
                +",colorred,coloryellow,colorblue,colorgreen,colorblack,colorwhite,colorgray"
                +",colororange,colorbeige,colorbrown,colorlime,colorturquoise,colorteal,colorpurple";
        return getOverlayNames(gob).stream().anyMatch(overlayName -> {
            return list.contains(overlayName.replace("gfx/terobjs/barrel-",""));
        });
    }

    public static void barrelTakeAllSeeds(Gob gob){
        ZeeManagerGobClick.gobClick(gob, 3, UI.MOD_CTRL_SHIFT);
    }

    private void barrelTakeAllSeeds() {
        barrelTakeAllSeeds(gob);
    }

    public static boolean isInventoryFull() {
        return ZeeConfig.getMainInventory().getNumberOfFreeSlots() == 0;
    }

    public static void destroyGob(Gob gob) {
        ZeeConfig.gameUI.menu.wdgmsg("act","destroy","0");
        gobClick(gob,1);
    }
    private void destroyGob() {
        destroyGob(gob);
    }

    public static void liftGob(Gob gob) {
        if(isGobBush(gob.getres().name)) {
            ZeeManagerItemClick.equipBeltItem("shovel");
            waitItemInHand("shovel");
        }
        ZeeConfig.gameUI.menu.wdgmsg("act", "carry","0");
        waitCursorName(ZeeConfig.CURSOR_HAND);
        gobClick(gob,1);
        waitPlayerDistToGob(gob,0);
    }

    static boolean isMidclickInspecting = false; // used by inspect tooltip feature
    public static void inspectGob(Gob gob){
        if (gob==null) {
            println("inspectGob > gob null");
            return;
        }
        isMidclickInspecting = true;
        new ZeeThread(){
            @Override
            public void run() {
                try {
                    ZeeConfig.gameUI.menu.wdgmsg("act","inspect","0");
                    sleep(50);
                    gobClick(gob, 1);
                    sleep(50);
                    ZeeConfig.clickRemoveCursor();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                isMidclickInspecting = false;
            }
        }.start();
    }

    public static boolean isGobTrellisPlant(String gobName) {
        return isGobInListEndsWith(gobName, "plants/wine,plants/hops,plants/pepper,plants/peas,plants/cucumber");
    }

    public static boolean isGobCrop(String gobName){
        return isGobInListEndsWith(gobName,"plants/carrot,plants/beet,plants/yellowonion,plants/redonion,"
                +"plants/leek,plants/lettuce,plants/pipeweed,plants/hemp,plants/flax,"
                +"plants/turnip,plants/millet,plants/barley,plants/wheat,plants/poppy,"
                +"plants/pumpkin,plants/fallowplant"
        );
    }

    static boolean isGobHarvestable(String gobName){
        return isGobCrop(gobName) || isGobTrellisPlant(gobName);
    }

    public static boolean isGobCraftingContainer(String gobName) {
        String containers ="cupboard,chest,crate,basket,box,coffer,cabinet";
        return isGobInListEndsWith(gobName,containers);
    }


    private static boolean isGobInListContains(String gobName, String list) {
        String[] names = list.split(",");
        for (int i = 0; i < names.length; i++) {
            if (gobName.contains(names[i])){
                return true;
            }
        }
        return false;
    }

    private static boolean isGobInListEndsWith(String gobName, String list) {
        String[] names = list.split(",");
        for (int i = 0; i < names.length; i++) {
            if (gobName.endsWith(names[i])){
                return true;
            }
        }
        return false;
    }

    private boolean isGobInListStartsWith(String gobName, String list) {
        String[] names = list.split(",");
        for (int i = 0; i < names.length; i++) {
            if (gobName.startsWith(names[i])){
                return true;
            }
        }
        return false;
    }

    public static boolean clickGobPetal(Gob gob, String petalName) {
        if (gob==null){
            println(">clickGobPetal gob null");
            return false;
        }
        //make sure cursor is arrow before clicking gob
        if (!ZeeConfig.getCursorName().contentEquals(ZeeConfig.CURSOR_ARW)){
            ZeeConfig.clickRemoveCursor();
            if (!waitCursorName(ZeeConfig.CURSOR_ARW)) {
                return false;
            }
        }
        //click gob
        gobClick(gob,3);
        //click petal
        if(waitFlowerMenu()){
            choosePetal(getFlowerMenu(), petalName);
            return waitNoFlowerMenu();
        }else{
            //println("clickGobPetal > no flower menu?");
            return false;
        }
    }

    // if gob has flowermenu returns true
    public static boolean gobExistsBecauseFlowermenu(Gob gob) {

        boolean ret;

        //no gob, no menu
        if (ZeeConfig.isGobRemoved(gob)) {
            //println(">gobHasFlowermenu gob is inexistent");
            return false;
        }

        //select arrow cursor if necessary
        if (!ZeeConfig.getCursorName().contentEquals(ZeeConfig.CURSOR_ARW)) {
            ZeeConfig.clickRemoveCursor();
            waitCursorName(ZeeConfig.CURSOR_ARW);
        }

        //disable auto options before clicking gob
        boolean butchBackup = ZeeConfig.butcherMode;
        ZeeConfig.butcherMode = false;
        ZeeConfig.autoClickMenuOption = false;

        //click gob and wait menu
        gobClick(gob, 3);
        if (waitFlowerMenu()) {
            // menu opened means gob exist
            ZeeConfig.cancelFlowerMenu();
            waitNoFlowerMenu();
            //println("gobHasFlowermenu > true");
            ret = true;
        } else {
            //println("gobHasFlowermenu > cant click gob");
            ret = false;
        }

        //restore settings and return
        ZeeConfig.butcherMode = butchBackup;
        ZeeConfig.autoClickMenuOption = Utils.getprefb("autoClickMenuOption", true);
        return ret;
    }

    static boolean isGobTamedAnimal(String gobName){
        return isGobInListEndsWith(
                gobName,
                "/stallion,/mare,/foal,/hog,/sow,/piglet,"
                    +"/billy,/nanny,/kid,/sheep,/lamb,/cattle,/calf,"
        );
    }

    static boolean isGobButchable(String gobName){
        return isGobInListEndsWith(
            gobName,
            "/stallion,/mare,/foal,/hog,/sow,/piglet,"
            +"/billy,/nanny,/kid,/sheep,/lamb,/cattle,/calf,"
            +"/wildhorse,/aurochs,/mouflon,/wildgoat,"
            +"/adder,/badger,/bear,/boar,/beaver,/fox,"
            +"/reindeer,/reddeer,/roedeer,"
            +"/greyseal,/otter,"
            +"/lynx,/mammoth,/moose,/troll,/walrus,/wolf,/wolverine,"
            +"/caveangler,/boreworm,/caverat,/cavelouse"
        );
    }

    static boolean isGobHorse(String gobName) {
        return isGobInListEndsWith(gobName, "stallion,mare,horse");
    }

    static boolean isGobFireSource(Gob gob) {
        String gobName = gob.getres().name;
        if ( isGobInListEndsWith(gobName,"/brazier,/snowlantern,/pow,/bonfire") )
            if (getOverlayNames(gob).contains("gfx/fx/flight"))
                return true;
        return false;
    }

    static boolean isGobFireTarget(Gob gob) {
        String gobName = gob.getres().name;
        if ( isGobInListEndsWith(gobName,"/brazier,/snowlantern,/pow,/bonfire,/bpyre") )
            if (!getOverlayNames(gob).contains("gfx/fx/flight"))
                return true;
        return false;
    }

    /**
     * Itemact with gob, to fill trough with item in hand for example
     * @param mod 1 = shift, 2 = ctrl, 4 = alt  (3 = ctrl+shift ?)
     */
    public static void itemActGob(Gob g, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("itemact", Coord.z, g.rc.floor(OCache.posres), mod, 0, (int) g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    public static void gobClick(Gob g, int btn, int mod, int x) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), g.rc.floor(OCache.posres), btn, mod, 0, (int)g.id, g.rc.floor(OCache.posres), 0, x);
    }

    public static void gobClick(Gob g, int btn) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), g.rc.floor(OCache.posres), btn, 0, 0, (int)g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    static void gobClick(Gob g, int btn, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), g.rc.floor(OCache.posres), btn, mod, 0, (int)g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    static void gobPlace(Gob g, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("place", g.rc.floor(posres), (int) Math.round(g.a * 32768 / Math.PI), 1, mod);
    }

    static void gobPlace(Gob g, Coord c, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("place", c, (int) Math.round(g.a * 32768 / Math.PI), 1, mod);
    }

    static double distanceCoordGob(Coord2d c, Gob gob) {
        return c.dist(gob.rc);
    }

    // return Gob or null
    public static Gob findGobById(long id) {
        return ZeeConfig.gameUI.ui.sess.glob.oc.getgob(id);
    }

    // "gfx/terobjs/barrel-flax"
    public static List<String> getOverlayNames(Gob gob) {
        List<String> ret = new ArrayList<>();
        for (Gob.Overlay ol : gob.ols) {
            if(ol.res != null)
                ret.add(ol.res.get().name);
        }
        return ret;
    }

    public static List<String> getGAttrNames(Gob gob) {
        List<String> ret = new ArrayList<>();
        Collection<GAttrib> attrs = gob.attr.values();
        for (GAttrib attr : attrs) {
            ret.add(attr.getClass().getSimpleName());
        }
        return ret;
    }

    public static Gob getGobFromClickable(Clickable ci) {
        if(ci instanceof Gob.GobClick) {
            return ((Gob.GobClick) ci).gob;
        } else if(ci instanceof Composited.CompositeClick) {
            Gob.GobClick gi = ((Composited.CompositeClick) ci).gi;
            return gi != null ? gi.gob : null;
        }
        return null;
    }

    // pattern must match whole gob name
    static boolean pickupGobIsShiftDown;
    static boolean picking = false;
    public static void pickupClosestGob(KeyEvent ev) {

        picking = true;

        pickupGobIsShiftDown = ev.isShiftDown();

        // find eligible gobs
        List<Gob> gobs = findPickupGobs();

        if (gobs==null || gobs.size()==0) {
            picking = false;
            return;
        }

        // calculate closest gob
        double minDist=99999, dist;
        Gob closestGob=null;
        String name;
        for (int i = 0; i < gobs.size(); i++) {
            Gob g = gobs.get(i);
            dist = ZeeConfig.distanceToPlayer(g);
            name = g.getres().name;
            if ( closestGob == null ){
                minDist = dist;
                closestGob = g;
            }
            else if( (g.pickupPriority = (( ZeeConfig.isBug(name) || name.contains("/kritter/")) && dist < 88)) || dist < minDist)
            {
                // prev closest gob had priority
                if (closestGob.pickupPriority && !g.pickupPriority){
                    continue;
                }
                minDist = dist;
                closestGob = g;
            }
        }

        // pickup closest gob
        if (closestGob!=null) {
            // right click gob
            if ( ZeeConfig.isBug(closestGob.getres().name)
                || closestGob.getres().name.contains("/kritter/")
                || closestGob.getres().name.contains("/terobjs/items/"))
            {
                if (pickupGobIsShiftDown)
                    gobClick(closestGob, 3, UI.MOD_SHIFT);
                else
                    gobClick(closestGob, 3);
            }
            // select "Pick" menu option
            else {
                Gob finalClosestGob = closestGob;
                new ZeeThread(){
                    public void run() {
                        clickGobPetal(finalClosestGob,"Pick");
                    }
                }.start();
            }
        }

        picking = false;
    }

    private static List<Gob> findPickupGobs() {
        List<Gob> gobs = ZeeConfig.getAllGobs();
        gobs.removeIf(gob1 ->{
            String name = gob1.getres().name;
            //dont remove items, herbs, bugs
            if (name.contains("/items/") || name.contains("/herbs/") || ZeeConfig.isBug(name))
                return false;
            // kritters
            if ( name.contains("/kritter/")) {
                if ( ZeeConfig.isKritterNotPickable(name) )
                    return true; // remove non pickable kritter
                else
                    return false;
            }
            //remove leafpile
            if ( name.contentEquals("gfx/terobjs/herbs/leafpile") )
                return true;
            //remove all else
            return true;
        });
        return gobs;
    }

    static ZeeWindow winPickupGob;
    static void toggleWindowPickupGob() {

        // find eligible gobs
        List<Gob> gobs = findPickupGobs();

        Widget wdg = null;
        List<String> listNamesAdded = new ArrayList<>();

        // toggle window off
        if (winPickupGob!=null){
            winPickupGob.reqdestroy();
            winPickupGob = null;
            listNamesAdded.clear();
        }

        //create window
        winPickupGob = new ZeeWindow(Coord.of(200,300),"Pickup Gobs");

        //checkbox keep window open
        wdg = winPickupGob.add( new CheckBox("keep window open"){
            {
                a = ZeeConfig.pickupGobWindowKeepOpen;
            }
            public void set(boolean val) {
                ZeeConfig.pickupGobWindowKeepOpen = val;
                a = val;
            }
        }, 0, 0 );


        //button refresh
        wdg = winPickupGob.add(new ZeeWindow.ZeeButton(60,"refresh"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    toggleWindowPickupGob();
                }
            }
        }, 0, wdg.c.y + wdg.sz.y + 5);
        wdg = winPickupGob.add(new Label("(ctrl+q)"), wdg.c.x + wdg.sz.x + 5 , wdg.c.y + 5);

        //scroll port
        int y = wdg.c.y + wdg.sz.y + 15;
        Scrollport scrollport = winPickupGob.add(new Scrollport(new Coord(130, 200)), 0, y);


        // add window, exit if no gobs
        winPickupGob.pack();
        ZeeConfig.gameUI.add(winPickupGob);
        ZeeConfig.windowFitView(winPickupGob);
        ZeeConfig.windowGlueToBorder(winPickupGob);
        if (gobs==null || gobs.size()==0)
            return;


        // populate window with gob list
        y = 0;//inside port
        for (int i = 0; i < gobs.size(); i++) {

            String resname = gobs.get(i).getres().name;
            String basename = gobs.get(i).getres().basename();

            //avoid duplicates
            if (listNamesAdded.contains(resname))
                continue;

            //avoid big kritters
            if (ZeeConfig.isKritter(resname) && ZeeConfig.isKritterNotPickable(resname))
                continue;

            // avoid duplicate names
            listNamesAdded.add(resname);

            // add button "pick" single gob
            wdg = scrollport.cont.add(
                new ZeeWindow.ZeeButton(30, "one"){
                    public void wdgmsg(String msg, Object... args) {
                        if (msg.contentEquals("activate")){
                            //pickup closest matching
                            Gob closest = ZeeConfig.getClosestGobByNameContains("/"+basename);
                            if (closest!=null) {
                                gobClick(closest, 3);
                            }
                            if (!ZeeConfig.pickupGobWindowKeepOpen)
                                winPickupGob.wdgmsg("close");
                        }
                    }
                },
                0,y
            );

            // add button pick "all"
            wdg = scrollport.cont.add(
                    new ZeeWindow.ZeeButton(30, "all") {
                        public void wdgmsg(String msg, Object... args) {
                            if (msg.contentEquals("activate")) {
                                //pickup closest matching
                                Gob closest = ZeeConfig.getClosestGobByNameContains("/"+basename);
                                if (closest!=null) {
                                    gobClick(closest, 3, UI.MOD_SHIFT);
                                }
                                if (!ZeeConfig.pickupGobWindowKeepOpen)
                                    winPickupGob.wdgmsg("close");
                            }
                        }
                    },
                    wdg.c.x + wdg.sz.x, y
            );
            if (!resname.contains("/terobjs/")) {
                // disable button all if not a terobj
                ((Button)wdg).disable(true);
            }

            // add label gob name
            wdg = scrollport.cont.add(new Label(basename), wdg.c.x+wdg.sz.x+3, y + 5);

            y += 23;
        }

        winPickupGob.pack();
        ZeeConfig.windowFitView(winPickupGob);
        ZeeConfig.windowGlueToBorder(winPickupGob);
    }

    private static void pickGobsFilterSort(List<Gob> gobs, String gobBaseName) {
        //filter gobs by selected name, sort by player dist
        gobs.stream()
        .filter(gob1 -> {
            if (!gob1.getres().basename().contentEquals(gobBaseName))
                return false;
            //avoid big kritters
            if (ZeeConfig.isKritter(gob1.getres().name) && ZeeConfig.isKritterNotPickable(gob1.getres().name))
                return false;
            return true;
        })
        .collect(Collectors.toList())
        .sort((gob1, gob2) -> {
            double d1 = ZeeConfig.distanceToPlayer(gob1);
            double d2 = ZeeConfig.distanceToPlayer(gob2);
            if ( d1 > d2 )
                return -1; //less than
            if ( d1 < d2 )
                return 1; // greater than
            return 0; // equal
        });
    }


    static boolean brightnessDefault() {
        Glob glob = ZeeConfig.gameUI.ui.sess.glob;
        glob.blightamb = glob.lightamb;
        Utils.setprefi(getLightPrefName(), 1);
        ZeeConfig.msgLow("amblight default "+glob.blightamb.getRed());
        brightnessMsg(true);
        return true;
    }

    static boolean brightnessDown() {
        Glob glob = ZeeConfig.gameUI.ui.sess.glob;
        glob.blightamb = colorStep(glob.blightamb,-15);
        Utils.setprefi(getLightPrefName(), ZeeConfig.colorToInt(glob.blightamb));
        brightnessMsg(false);
        return true;
    }


    static boolean brightnessUp() {
        Glob glob = ZeeConfig.gameUI.ui.sess.glob;
        glob.blightamb = colorStep(glob.blightamb,15);
        Utils.setprefi(getLightPrefName(), ZeeConfig.colorToInt(glob.blightamb));
        brightnessMsg(false);
        return true;
    }

    private static void brightnessMsg(boolean defLight) {
        String msg = "";
        if (defLight) {
            msg = "default light ";
        }
        else {
            if (ZeeConfig.playerLocation == ZeeConfig.LOCATION_OUTSIDE)
                msg = "outside light ";
            else if (ZeeConfig.playerLocation == ZeeConfig.LOCATION_CELLAR)
                msg = "cellar light ";
            else if (ZeeConfig.playerLocation == ZeeConfig.LOCATION_CABIN)
                msg = "cabin light ";
            else if (ZeeConfig.playerLocation == ZeeConfig.LOCATION_UNDERGROUND)
                msg = "underground light ";
        }
        Glob glob = ZeeConfig.gameUI.ui.sess.glob;
        ZeeConfig.msgLow(msg + glob.blightamb.getRed());
    }

    private static Color colorStep(Color c, int step) {
        int red, green, blue;
        if (step < 0) {
            //reduce colors by step
            red = Math.max((int) ((double) c.getRed() + step), 0);
            green = Math.max((int) ((double) c.getGreen() + step), 0);
            blue = Math.max((int) ((double) c.getBlue() + step), 0);
        }else{
            //increase colors by step
            red = Math.min((int) ((double) c.getRed() + step), 255);
            green = Math.min((int) ((double) c.getGreen() + step), 255);
            blue = Math.min((int) ((double) c.getBlue() + step), 255);
        }
        return new Color(red,green,blue,c.getAlpha());
    }

    static void brightnessMapLoad() {
        Glob glob = ZeeConfig.gameUI.ui.sess.glob;

        // save player location
        if (glob.blightamb.getRed() == ZeeConfig.DEF_LIGHT_CELLAR)
            ZeeConfig.playerLocation = ZeeConfig.LOCATION_CELLAR;
        else if (glob.blightamb.getRed() == ZeeConfig.DEF_LIGHT_CABIN)
            ZeeConfig.playerLocation = ZeeConfig.LOCATION_CABIN;
        else if (glob.blightamb.getRed() == ZeeConfig.DEF_LIGHT_UNDERGROUND)
            ZeeConfig.playerLocation = ZeeConfig.LOCATION_UNDERGROUND;
        else
            ZeeConfig.playerLocation = ZeeConfig.LOCATION_OUTSIDE;

        // restore saved brightness
        int intColor = Utils.getprefi(getLightPrefName(),1);
        if (intColor < 0) {
            glob.blightamb = ZeeConfig.intToColor(intColor);
        }
    }

    private static String getLightPrefName() {
        return "blightamb_" + ZeeConfig.playerLocation;
    }

    private static void windowTestCoords() {
        String name = "test coordss123";
        Window win = ZeeConfig.getWindow(name);
        if(win!=null){
            win.reqdestroy();
        }
        win = ZeeConfig.gameUI.add(new Window(Coord.of(225,100),name){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("close"))
                    this.reqdestroy();
            }
        });
        int x = win.csz().x;
        int y = win.csz().y;

        //up
        win.add(new Button(60,"up"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    windowTestCoordsMove(this);
                }
            }
        }, (int)(x*0.37),0);
        //down
        win.add(new Button(60,"down"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    windowTestCoordsMove(this);
                }
            }
        }, (int)(x*0.37),(int)(y*0.80));
        //left
        win.add(new Button(60,"left"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    windowTestCoordsMove(this);
                }
            }
        }, 0,(int)(y*0.40));
        //right
        win.add(new Button(60,"right"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    windowTestCoordsMove(this);
                }
            }
        }, (int)(x*0.75),(int)(y*0.40));
        //center
        win.add(new Button(60,"center"){
            public void wdgmsg(String msg, Object... args) {
                if (msg.contentEquals("activate")){
                    windowTestCoordsMove(this);
                }
            }
        }, (int)(x*0.37),(int)(y*0.40));
    }

    static void windowTestCoordsMove(Button button){
        new Thread(){
            public void run() {
                try {
                    Coord c1 = ZeeConfig.getPlayerTile();
                    Coord c2 = Coord.of(c1);
                    println(button.text.text+" ... ");
                    switch (button.text.text){
                        case "up":
                            c2 = c1.sub(0,1);
                            break;
                        case "down":
                            c2 = c1.add(0,1);
                            break;
                        case "left":
                            c2 = c1.sub(1,0);
                            break;
                        case "right":
                            c2 = c1.add(1,0);
                            break;
                    }
                    println("    moveTo "+c2);
                    ZeeConfig.moveToTile(c2);
                    waitPlayerIdlePose();
                    println("    final "+ZeeConfig.getPlayerTile());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    public static boolean isMouseUp() {
        return lastClickMouseDownMs < lastClickMouseUpMs;
    }

    static boolean isGobDeadOrKO(Gob gob) {
        return ZeeConfig.gobHasAnyPoseContains(gob,"/dead","/knock","-knock","/waterdead");
    }

    static void toggleHitbox() {
        if (ZeeConfig.showHitbox)
            ZeeConfig.msgLow("show hitbox");
        else
            ZeeConfig.msgLow("hide hitbox");

        List<Gob> gobs = ZeeConfig.getAllGobs();

        for (Gob gob : gobs) {
            gob.toggleHitbox();
        }
    }

    static void toggleModels() {

        //ZeeConfig.hideTreesPalisCrops = !ZeeConfig.hideTreesPalisCrops;

        if (ZeeConfig.hideTreesPalisCrops)
            ZeeConfig.msgLow("hide trees/pali/crops");
        else
            ZeeConfig.msgLow("show trees/pali/crops");

        Utils.setprefb("hideTreesPalisCrops", ZeeConfig.hideTreesPalisCrops);

        ZeeQuickOptionsWindow.updateCheckboxNoBump("hideTreesPalisCrops",ZeeConfig.hideTreesPalisCrops);

        // toggle gob models
        List<Gob> gobs = ZeeConfig.getAllGobs();
        //ZeeConfig.println("toggling "+gobs.size()+" models = "+ZeeConfig.hideTreesPalisCrops);
        for (Gob gob : gobs) {
            gob.toggleModel();
        }
    }

}
