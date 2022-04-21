package haven;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static haven.OCache.posres;

public class ZeeClickGobManager extends ZeeThread{

    Coord coordPc;
    Coord2d coordMc;
    Gob gob;
    String gobName;
    boolean isGroundClick;

    public static float camAngleStart, camAngleEnd, camAngleDiff;
    public static long clickStartMs, clickEndMs, clickDiffMs;
    public static boolean clickPetal = false;
    public static String clickPetalName = "";
    public static boolean barrelLabelOn = false;

    public ZeeClickGobManager(Coord pc, Coord2d mc, Gob gobClicked) {
        coordPc = pc;
        coordMc = mc;
        gob = gobClicked;
        isGroundClick = (gob==null);
        gobName = isGroundClick ? "" : gob.getres().name;
        clickDiffMs = clickEndMs - clickStartMs;
        ZeeConfig.getMainInventory();
        //println(clickDiffMs+"ms > "+gobName + (isGroundClick ? mc : " dist="+ZeeConfig.distanceToPlayer(gob)));
    }

    @Override
    public void run() {
        try {
            if (!isLongClick()) {
            /*
                short clicks
             */
                if (ZeeConfig.isPlayerHoldingItem()) {
                    clickedGobHoldingItem();
                } else if (isGobTrellisPlant()) {
                    harvestOneTrellis();
                } else if (isGobGroundItem()) {
                    gobClick(3, UI.MOD_SHIFT);//pick up all items (shift + rclick)
                    if (ZeeConfig.pilerMode)
                        ZeeStockpileManager.checkGroundItemClicked(gobName);
                } else if (isGobFireSource()) {
                    if (pickupTorch())
                        gobItemAct(0);
                } else if (isGobHorse()) {
                    mountHorse();
                } else if (isGobName("/barrel")) {
                    if (barrelLabelOn)
                        ZeeSeedFarmingManager.testBarrelsTilesClear();
                    else
                        ZeeSeedFarmingManager.testBarrelsTiles(true);
                    barrelLabelOn = !barrelLabelOn;
                } else if (isGobName("/dreca")) { // dream catcher
                    twoDreamsPlease();
                } else if (isInspectGob()) {
                    inspectGob();
                } else if (isGobMineSupport()) {
                    ZeeConfig.toggleMineSupport();
                }
            }
            else {
                /*
                    long clicks
                 */
                if (!isGroundClick && !ZeeConfig.isPlayerHoldingItem() && showGobFlowerMenu()) {
                    //ok
                } else if (isGobCrop()) {
                    if (!ZeeConfig.getCursorName().equals(ZeeConfig.CURSOR_HARVEST))
                        gobClick(gob, 3, UI.MOD_SHIFT);//activate cursor harvest if needed
                } else if (isGobStockpile() && ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    ZeeStockpileManager.unloadWheelbarrowAtStockpile(gob);
                } else if (isGobStockpile() || isGobName("/dframe")) {
                    gobClick(3, UI.MOD_SHIFT);//pick up all items (shift + rclick)
                } else if (isGobTreeStump()) {
                    removeStump(gob);
                } else if (ZeeConfig.isPlayerHoldingItem() && isGobName("/barrel")) {
                    if (ZeeSeedFarmingManager.isBarrelEmpty(gob))
                        gobItemAct(UI.MOD_SHIFT);//shift+rclick
                    else
                        gobItemAct(3);//ctrl+shift+rclick
                } else if (isGroundClick){
                    if (ZeeConfig.isPlayerMountingHorse())
                        dismountHorse();
                    else if (ZeeConfig.isPlayerCarryingWheelbarrow())
                        ZeeStockpileManager.unloadWheelbarrowStockpileAtGround(coordMc.floor(posres));
                } else if (ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    if (isGobHorse())
                        mountHorseCarryingWheelbarrow();
                    else
                        unloadWheelbarrowAtGob();
                } else if (!isGobName("/wheelbarrow") && ZeeConfig.isPlayerDrivingWheelbarrow()) {
                    if (isGobHorse())
                        mountHorseDrivingWheelbarrow();
                    else if (isGobGate())
                        openGateWheelbarrow();
                    else if (isGobName("/cart"))
                        liftAndStoreWheelbarrow();
                } else if (isLiftGob()) {
                    liftGob();
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private void unloadWheelbarrowAtGob() throws Exception {
        ZeeStockpileManager.unloadWheelbarrowAtStockpile(gob);
    }

    private void dismountHorse() {
        ZeeConfig.clickCoord(coordMc.floor(posres),1,UI.MOD_CTRL);
    }

    public static void mountHorse(Gob horse){
        int playerSpeed = ZeeConfig.getPlayerSpeed();
        clickGobPetal(horse,"Giddyup!");
        waitPlayerMounted(horse);
        if (ZeeConfig.isPlayerMountingHorse()) {
            if (playerSpeed <= ZeeConfig.PLAYER_SPEED_1)
                ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_1);//min auto horse speed
            else
                ZeeConfig.setPlayerSpeed(ZeeConfig.PLAYER_SPEED_2);//max auto horse speed
        }
    }
    private void mountHorse() {
        mountHorse(gob);
    }

    private void clickedGobHoldingItem() {
        if (isGobStockpile())
            gobItemAct(UI.MOD_SHIFT);//try piling all items
        else
            gobClick(gob,3,0); // try ctrl+click simulation
    }

    private void twoDreamsPlease() {
        if(clickGobPetal("Harvest")) {
            waitNoFlowerMenu();
            if(clickGobPetal("Harvest"))
                waitNoFlowerMenu();
        }
    }

    public static boolean pickupTorch() {
        if (ZeeClickItemManager.pickupBeltItem("/torch")) {
            return true;
        }else if(ZeeClickItemManager.pickupHandItem("/torch")){
            return true;
        }else if (ZeeClickItemManager.pickUpInvItem(ZeeConfig.getMainInventory(),"/torch")){
            return true;
        }
        return false;
    }


    public static void gobZeeMenuClicked(Gob gob, String petalName){

        String gobName = gob.getres().name;

        if (petalName.equals(ZeeFlowerMenu.STRPETAL_AUTO_BUTCH_BIG)){
            autoButchBigAnimal(gob);
        }
        else if (petalName.equals(ZeeFlowerMenu.STRPETAL_LIFTUPGOB)){
            liftGob(gob);
        }
        else if(gobName.endsWith("terobjs/oven")) {
            addFuelToGob(gob,petalName);
        }
        else if(gobName.endsWith("terobjs/smelter")){
            addFuelToGob(gob,petalName);
        }
        else if (isGobTrellisPlant(gobName)){
            if(petalName.equals(ZeeFlowerMenu.STRPETAL_REMOVEPLANT)) {
                destroyGob(gob);
            }else if (petalName.equals(ZeeFlowerMenu.STRPETAL_REMOVEALLPLANTS)){
                removeAllTrellisPlants(gob);
            }
        }
        else if(isGobTree(gobName)){
            removeTreeAndStump(gob,petalName);
        }
        else if (isGobCrop(gobName)) {
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_SEEDFARMER)) {
                ZeeSeedFarmingManager.showWindow(gob);
            }
            else if (petalName.equals(ZeeFlowerMenu.STRPETAL_CURSORHARVEST)) {
                if (!ZeeConfig.getCursorName().equals(ZeeConfig.CURSOR_HARVEST))
                    gobClick(gob, 3, UI.MOD_SHIFT);
            }
        }
        else if (isBarrelTakeAll(gob)) {
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_BARRELTAKEALL)) {
                barrelTakeAllSeeds(gob);
            }
        }
        else if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG2) || petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3) || petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG4)){
            destroyTreelogs(gob,petalName);
        }
        else if (petalName.equals(ZeeFlowerMenu.STRPETAL_SHOWWINDOWMINING)) {
            ZeeMiningManager.showWindowMining();
        }else{
            println("chooseGobFlowerMenu > unkown case");
        }
    }

    public static void autoButchBigAnimal(Gob gob) {
        new ZeeThread() {
            public void run() {
                boolean butcherBackup = ZeeConfig.butcherMode;
                ZeeConfig.butcherAutoList = ZeeConfig.DEF_BUTCH_AUTO_LIST;
                try{
                    ZeeConfig.addPlayerText("autobutch");
                    ZeeConfig.lastMapViewClickButton = 2;//prepare for clickCancelTask()
                    while (!ZeeConfig.clickCancelTask() && gobHasFlowermenu(gob)) {

                        //prepare settings and click gob
                        ZeeConfig.lastInvItemMs = 0;
                        ZeeConfig.butcherMode = true;
                        ZeeConfig.autoClickMenuOption = false;
                        gobClick(gob,3);

                        //wait inventory idle
                        if (ZeeClickItemManager.isItemEquipped("/butcherscleaver"))
                            waitInvIdleMs(1000);
                        else
                            waitInvIdleMs(2000);
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
        if (!ZeeClickItemManager.isItemEquipped("/bonesaw") || ZeeClickItemManager.isItemEquipped("/saw-m")){
            ZeeConfig.msg("need bone saw equipped, no metal saw");
            return;
        }
        try {
            waitNoFlowerMenu();
            Gob treelog = firstTreelog;
            String treelogName = treelog.getres().name;
            int logs = 2;
            if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3))
                logs = 3;
            else if (petalName.equals(ZeeFlowerMenu.STRPETAL_DESTROYTREELOG4))
                logs = 4;
            ZeeConfig.isDestroyingTreelogs = true;
            while (logs>0 && !ZeeConfig.clickCancelTask()){
                ZeeConfig.addPlayerText("destroying "+logs+" treelog(s)");
                clickGobPetal(treelog,"Make boards");
                //waitNoFlowerMenu();
                waitStaminaIdleMs(3500);//no stam change means treelog is done
                //if (waitGobRemovedSeconds(treelog,10) && !ZeeConfig.clickCancelTask()){
                if (!gobHasFlowermenu(treelog) && !ZeeConfig.clickCancelTask()){
                    logs--;
                    treelog = ZeeConfig.getClosestGobName(treelogName);
                }else{
                    if (ZeeConfig.clickCancelTask()) {
                        ZeeConfig.msg("destroy treelog canceled by click");
                        println("destroy treelog canceled by click");
                    }
                    logs = 0;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.isDestroyingTreelogs = false;
        ZeeConfig.removePlayerText();
    }


    private boolean showGobFlowerMenu(){

        boolean showMenu = true;
        ZeeFlowerMenu menu = null;

        if (isGobBigAnimal()) {
            showMenu = isGobBigDeadAnimal_thread();//thread wait
            if (showMenu)
                menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_AUTO_BUTCH_BIG, ZeeFlowerMenu.STRPETAL_LIFTUPGOB);
        }
        else if(gobName.endsWith("terobjs/oven")){
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_ADD4BRANCH);
        }
        else if(gobName.endsWith("terobjs/smelter")){
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_ADD9COAL, ZeeFlowerMenu.STRPETAL_ADD12COAL);
        }
        else if (isGobTrellisPlant()){
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_REMOVEPLANT, ZeeFlowerMenu.STRPETAL_REMOVEALLPLANTS);
        }
        else if (isGobTree()){
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_REMOVETREEANDSTUMP);
        }
        else if (isGobCrop()) {
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_SEEDFARMER, ZeeFlowerMenu.STRPETAL_CURSORHARVEST);
        }
        else if (isBarrelTakeAll()) {
            menu = new ZeeFlowerMenu(gob,ZeeFlowerMenu.STRPETAL_BARRELTAKEALL, ZeeFlowerMenu.STRPETAL_LIFTUPGOB);
        }
        else if (isDestroyTreelog()) {
            menu = new ZeeFlowerMenu( gob,
                ZeeFlowerMenu.STRPETAL_LIFTUPGOB, ZeeFlowerMenu.STRPETAL_DESTROYTREELOG2,
                ZeeFlowerMenu.STRPETAL_DESTROYTREELOG3, ZeeFlowerMenu.STRPETAL_DESTROYTREELOG4
            );
        }
        else if (gobName.endsWith("/column")) {
            menu = new ZeeFlowerMenu(gob, ZeeFlowerMenu.STRPETAL_SHOWWINDOWMINING);
        }else{
            showMenu = false;
            //println("showGobFlowerMenu() > unkown case");
        }

        if (showMenu) {
            ZeeConfig.gameUI.ui.root.add(menu, coordPc);
        }

        return showMenu;
    }

    static boolean isGobDeadAnimal;
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
                        if (ZeeConfig.DEF_BUTCH_AUTO_LIST.contains(fm.opts[i].name)){
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
            ZeeConfig.butcherMode = false;

            //start thread and wait it finish
            isGobDeadAnimal = false;
            zt.start();
            zt.join();//wait thread

            //restore automenu settings
            ZeeConfig.autoClickMenuOption = Utils.getprefb("autoClickMenuOption", true);

            return isGobDeadAnimal;

        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    private boolean isDestroyTreelog() {
        if(isGobTreeLog() && ZeeClickItemManager.isItemEquipped("bonesaw"))
            return true;
        return false;
    }

    private void mountHorseDrivingWheelbarrow(){
        Gob horse = gob;
        try{
            //waitNoFlowerMenu();
            ZeeConfig.addPlayerText("mounting");
            Gob wb = ZeeConfig.getClosestGobName("gfx/terobjs/vehicle/wheelbarrow");
            if (wb == null) {
                ZeeConfig.msg("no wheelbarrow close");
            } else {
                Coord pc = ZeeConfig.getPlayerCoord();
                Coord subc = ZeeConfig.getCoordGob(horse).sub(pc);
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

    private void mountHorseCarryingWheelbarrow() {
        Gob horse = gob;
        try {
            //waitNoFlowerMenu();
            ZeeConfig.addPlayerText("mounting");
            Gob wb = ZeeConfig.getClosestGobName("gfx/terobjs/vehicle/wheelbarrow");
            if (wb == null) {
                ZeeConfig.msg("no wheelbarrow close");
            } else {
                Coord pc = ZeeConfig.getPlayerCoord();
                Coord subc = ZeeConfig.getCoordGob(horse).sub(pc);
                int xsignal, ysignal;
                xsignal = subc.x >= 0 ? -1 : 1;
                ysignal = subc.y >= 0 ? -1 : 1;
                //try to drop wheelbarrow away from horse direction
                ZeeConfig.clickCoord(pc.add(xsignal * 500, ysignal * 500), 3);
                sleep(500);
                //if drop wb success
                if (!ZeeConfig.isPlayerCarryingWheelbarrow()) {
                    ZeeConfig.clickRemoveCursor();//remove hand cursor
                    mountHorse();//mount horse
                    waitPlayerMounted(horse);
                    liftGob(wb);//lift wheelbarrow
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private void liftAndStoreWheelbarrow(){
        Gob storage = gob;
        try {
            waitNoFlowerMenu();
            ZeeConfig.addPlayerText("storing");
            Gob wb = ZeeConfig.getClosestGobName("gfx/terobjs/vehicle/wheelbarrow");
            if (wb==null){
                ZeeConfig.msg("no wheelbarrow close");
            }else {
                double dist;
                ZeeConfig.clickRemoveCursor();//remove hand cursor
                liftGob(wb);
                dist = ZeeConfig.distanceToPlayer(wb);
                if (dist==0) {
                    gobClick(storage, 3);
                    waitPlayerIdleFor(1);
                }else{
                    ZeeConfig.msg("wheelbarrow unreachable?");//impossible case?
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
    }

    private void openGateWheelbarrow() {
        // gfx/terobjs/vehicle/wheelbarrow
        Gob gate = gob;
        try {
            waitNoFlowerMenu();
            ZeeConfig.addPlayerText("wheeling");
            Gob wb = ZeeConfig.getClosestGobName("gfx/terobjs/vehicle/wheelbarrow");
            if (wb==null){
                ZeeConfig.msg("no wheelbarrow close");
            }else {
                double dist = ZeeConfig.distanceToPlayer(wb);
                ZeeConfig.clickRemoveCursor();//remove hand cursor
                liftGob(wb);
                //waitPlayerIdleFor(1);
                dist = ZeeConfig.distanceToPlayer(wb);
                if (dist==0) {
                    gobClick(gate, 3);
                    waitPlayerIdleFor(1);
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

    public boolean isGobGate() {
        if (gobName.startsWith("gfx/terobjs/arch/") && gobName.endsWith("gate"))
            return true;
        return false;
    }


    // barrel is empty if has no overlays ("gfx/terobjs/barrel-flax")
    public static boolean isBarrelEmpty(Gob barrel){
        return ZeeClickGobManager.getOverlayNames(barrel).isEmpty();
    }

    private static void removeAllTrellisPlants(Gob firstPlant) {
        try{
            String gobName = firstPlant.getres().basename();
            ZeeConfig.addGobText(ZeeConfig.getPlayerGob(),"removing all "+gobName);
            waitNoFlowerMenu();
            waitPlayerIdleFor(1);
            Gob closestPlant = firstPlant;
            double dist;
            do{
                if (ZeeConfig.clickCancelTask()) {
                    // cancel if clicked right/left button
                    println("cancel click");
                    break;
                }
                ZeeConfig.addGobText(closestPlant,"next");
                destroyGob(closestPlant);
                waitGobRemoved(closestPlant);
                closestPlant = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameContains(gobName));
                dist = ZeeConfig.distanceToPlayer(closestPlant);
                //println("dist "+dist);
            }while(dist < 25);
        } catch (Exception e) {
            e.printStackTrace();
        }
        ZeeConfig.removeGobText(ZeeConfig.getPlayerGob());
    }

    public static void removeTreeAndStump(Gob gob, String petalName){
        try{
            ZeeConfig.addGobText(ZeeConfig.getPlayerGob(),"removing tree & stump...");
            waitNoFlowerMenu();
            ZeeClickItemManager.equipAxeChopTree();
            clickGobPetal(gob,"Chop");
            if(waitStaminaIdleMs(3000)){
                Gob stump = ZeeConfig.getClosestGob(ZeeConfig.findGobsByNameEndsWith("stump"));
                if (stump!=null && ZeeConfig.distanceToPlayer(stump) < 25) {
                    ZeeConfig.addGobText(stump,"stump");
                    removeStump(stump);
                }else {
                    println("no stump close");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ZeeConfig.removeGobText(ZeeConfig.getPlayerGob());
    }

    public static void removeStump(Gob gob) {
        ZeeClickItemManager.equipBeltItem("shovel");
        waitItemEquipped("shovel");
        destroyGob(gob);
    }

    public static void addFuelToGob(Gob gob, String petalName) {
        String gobName = gob.getres().name;
        if(gobName.endsWith("oven") && petalName.equals(ZeeFlowerMenu.STRPETAL_ADD4BRANCH)){
            /*
                fuel oven with 4 branches
             */
           List<WItem> branches = ZeeConfig.getMainInventory().getWItemsByName("branch");
           if(branches.size() < 4){
               ZeeConfig.gameUI.msg("Need 4 branches to fuel oven");
               return;
           }
           boolean exit = false;
           int added = 0;
           while(!exit && added<4 && branches.size() > 0){
               if(ZeeClickItemManager.pickUpItem(branches.get(0))){
                   gobItemAct(gob,0);
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
            List<WItem> coal = ZeeConfig.getMainInventory().getWItemsByName("coal");
            if(coal.size() < numCoal){
                ZeeConfig.gameUI.msg("Need "+numCoal+" coal to fuel smelter");
                return;
            }
            boolean exit = false;
            int added = 0;
            while(!exit && added<numCoal && coal.size() > 0){
                if(ZeeClickItemManager.pickUpItem(coal.get(0))){
                    gobItemAct(gob,0);
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

    private boolean isFuelAction() {
        if (gobName.endsWith("oven") || gobName.endsWith("smelter")){
            return true;
        }
        return false;
    }

    private void harvestOneTrellis() {
        if(ZeeClickItemManager.pickupBeltItem("scythe")){
            //hold scythe for user unequip it
        }else if(ZeeClickItemManager.getLeftHandName().endsWith("scythe")){
            //hold scythe for user unequip it
            ZeeClickItemManager.unequipLeftItem();
        }else{
            //no scythe around, just harvest
            clickGobPetal("Harvest");
        }
    }

    private void pickupClosestGob() {
        List<Gob> gobs = ZeeConfig.gameUI.ui.sess.glob.oc.gobStream().filter(gob1 -> {
            if (gob1 == null || gob1.getres()==null)
                return false;
            return gob1.getres().name.startsWith("gfx/terobjs/herbs/") ||
                    gob1.getres().name.startsWith("gfx/terobjs/items/");
        }).collect(Collectors.toList());
        if(gobs.size()==0) {
            System.out.println("no gobs herbs/item");
            return;
        }
        Gob closestGob = gobs.get(0);
        double closestDist = distanceCoordGob(coordMc, closestGob);
        double dist;
        for (Gob g: gobs) {
            dist = distanceCoordGob(coordMc, g);
            System.out.println(g.getres().name+" > "+dist);
            if(closestDist > dist) {
                closestDist = dist;
                closestGob = g;
            }
        }
        gobClick(closestGob,3);//pickup item (right click)
        //System.out.println("closest = "+closestGob.getres().name+" > "+closestDist);
    }

    private boolean isGobStockpile() {
        return gobName.startsWith("gfx/terobjs/stockpile");
    }

    private boolean isGobGroundItem() {
        return gobName.startsWith("gfx/terobjs/items/");
    }

    public static boolean isLongClick() {
        clickDiffMs = clickEndMs - clickStartMs;
        return clickDiffMs > LONG_CLICK_MS;
    }

    private boolean isInspectGob(){
        if(isGobTree() || isGobBush() || isGobBoulder())
            return true;
        String list = "/meatgrinder,/potterswheel,/well,/dframe,"
                +"/smelter,/crucible,/steelcrucible,/fineryforge,/kiln,/tarkiln,/oven,"
                +"/compostbin,/gardenpot,/beehive,/htable,/bed-sturdy,/boughbed,/alchemiststable,"
                +"/gemwheel,/spark,/cauldron,/churn,/chair-rustic,"
                +"/royalthrone,curdingtub,log,/still,/oldtrunk,/anvil,"
                +"/loom,/swheel,knarr,snekkja,dock,"
                +"/ttub,/cheeserack,/dreca,/glasspaneframe";
        return gobNameEndsWith(gobName, list);
    }


    public boolean isGobMineSupport() {
        String list = "/minebeam,/column,/minesupport";
        return gobNameEndsWith(gobName, list);
    }


    private boolean isLiftGob() {
        if(isGobBush()) {
            ZeeClickItemManager.equipBeltItem("shovel");
            return waitNotHoldingItem();
        }
        if(isGobBoulder())
            return true;
        String endList = "/meatgrinder,/potterswheel,/iconsign,/rowboat,/dugout,/wheelbarrow,"
                +"/compostbin,/gardenpot,/beehive,/htable,/bed-sturdy,/boughbed,/alchemiststable,"
                +"/gemwheel,/ancestralshrine,/spark,/cauldron,/churn,/table-rustic,/chair-rustic,"
                +"/royalthrone,/trough,curdingtub,/plow,/barrel,/still,log,/oldtrunk,chest,/anvil,"
                +"/cupboard,/studydesk,/demijohn,/quern,/wreckingball-fold,/loom,/swheel,"
                +"/ttub,/cheeserack,/archerytarget,/dreca,/glasspaneframe,/runestone";
        return gobNameEndsWith(gobName,endList);
    }

    private boolean isGobBoulder() {
        return gobName.startsWith("gfx/terobjs/bumlings/") &&
               !gobName.startsWith("gfx/terobjs/bumlings/ras");
    }

    private boolean isGobBush() {
        return gobName.startsWith("gfx/terobjs/bushes");
    }

    public static boolean isGobTreeStump(String gobName) {
        return gobName.startsWith("gfx/terobjs/trees/") && gobName.endsWith("stump");
    }
    private boolean isGobTreeStump() {
        return isGobTreeStump(gobName);
    }

    private boolean isGobTree() {
        return isGobTree(gobName);
    }
    public static boolean isGobTree(String gobName) {
        return gobName.startsWith("gfx/terobjs/trees/") && !gobName.endsWith("log") && !gobName.endsWith("stump");
    }

    private boolean isGobTreeLog() {
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
    private boolean isBarrelTakeAll() {
        return isBarrelTakeAll(gob);
    }

    public static void barrelTakeAllSeeds(Gob gob){
        try{
            // shift+rclick last barrel
            ZeeClickGobManager.gobClick(gob, 3, UI.MOD_SHIFT);

            //wait getting to the barrel
            waitPlayerIdleFor(1);

            if (ZeeConfig.distanceToPlayer(gob) > ZeeSeedFarmingManager.MIN_ACCESSIBLE_DIST) {
                ZeeConfig.msg("barrel unreachable");
                return;
            }

            ZeeConfig.addPlayerText("taking contents...");

            while (!ZeeClickGobManager.isBarrelEmpty(gob) && !isInventoryFull()) {
                ZeeClickGobManager.gobClick(gob, 3, UI.MOD_SHIFT);
                Thread.sleep(PING_MS);
                if (ZeeConfig.clickCancelTask())
                    break;
            }

            //if holding seed, store in barrel
            waitHoldingItem();
            ZeeClickGobManager.gobItemAct(gob, 0);

            if (isInventoryFull())
                ZeeConfig.msg("Inventory full");
            else if (!ZeeConfig.clickCancelTask())
                ZeeConfig.msg("Took everything");

        }catch(Exception e){
            e.printStackTrace();
        }
        ZeeConfig.removePlayerText();
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
        ZeeConfig.gameUI.menu.wdgmsg("act", "carry","0");
        gobClick(gob,1);
        waitPlayerIdleFor(1);
    }
    private void liftGob() {
        liftGob(gob);
    }

    private void inspectGob() {
        ZeeConfig.gameUI.menu.wdgmsg("act","inspect","0");
        gobClick(1);
        //waitPlayerIdleFor(1);
        ZeeConfig.clickRemoveCursor();
    }

    public static boolean isGobTrellisPlant(String gobName) {
        return gobNameEndsWith(gobName, "plants/wine,plants/hops,plants/pepper,plants/peas,plants/cucumber");
    }
    public boolean isGobTrellisPlant() {
        return isGobTrellisPlant(gobName);
    }

    public static boolean isGobCrop(String gobName){
        return gobNameEndsWith(gobName,"plants/carrot,plants/beet,plants/yellowonion,plants/redonion,"
                +"plants/leek,plants/lettuce,plants/pipeweed,plants/hemp,plants/flax,"
                +"plants/turnip,plants/millet,plants/barley,plants/wheat,plants/poppy,"
                +"plants/pumpkin,plants/fallowplant"
        );
    }
    public boolean isGobCrop() {
        return isGobCrop(gobName);
    }

    public static boolean isGobCookContainer(String gobName) {
        String containers ="cupboard,chest,crate,basket,box,coffer,cabinet";
        return gobNameEndsWith(gobName,containers);
    }

    private static boolean gobNameEndsWith(String gobName, String list) {
        String[] names = list.split(",");
        for (int i = 0; i < names.length; i++) {
            if (gobName.endsWith(names[i])){
                return true;
            }
        }
        return false;
    }

    private boolean gobNameStartsWith(String list) {
        String[] names = list.split(",");
        for (int i = 0; i < names.length; i++) {
            if (gobName.startsWith(names[i])){
                return true;
            }
        }
        return false;
    }

    private boolean isGobName(String name) {
        return gobName.endsWith(name);
    }

    private boolean clickGobPetal(String petalName) {
        //ZeeClickGobManager.scheduleClickPetalOnce(petalName);
        gobClick(3);
        if(waitFlowerMenu()){
            //println("clickGobPetal1 > choosing "+petalName);
            return choosePetal(getFlowerMenu(), petalName);
        }else{
            //println("clickGobPetal1 > no flower menu?");
            return false;
        }
    }

    public static boolean clickGobPetal(Gob gob, String petalName) {
        if (gob==null){
            println(">clickGobPetal gob null");
            return false;
        }
        gobClick(gob,3);
        if(waitFlowerMenu()){
            choosePetal(getFlowerMenu(), petalName);
            return waitNoFlowerMenu();
        }else{
            //println("clickGobPetal > no flower menu?");
            return false;
        }
    }

    // if gob has flowermenu returns true
    public static boolean gobHasFlowermenu(Gob gob) {
        boolean ret;
        if (ZeeConfig.isGobRemoved(gob)) {
            println(">gobHasFlowermenu gob is inexistent");
            return false;
        }

        //select arrow cursor
        ZeeConfig.clickRemoveCursor();
        waitCursor(ZeeConfig.CURSOR_ARW);

        //disable auto options before clicking gob
        boolean butchBackup = ZeeConfig.butcherMode;
        ZeeConfig.butcherMode = ZeeConfig.autoClickMenuOption = false;

        //click gob and wait menu
        gobClick(gob, 3);
        if (waitFlowerMenu()) {
            // menu opened means gob exist
            ZeeConfig.cancelFlowerMenu();
            waitNoFlowerMenu();
            println("gobHasFlowermenu > true");
            ret = true;
        } else {
            println("gobHasFlowermenu > cant click gob");
            ret = false;
        }

        //restore settings and return
        ZeeConfig.butcherMode = butchBackup;
        ZeeConfig.autoClickMenuOption = Utils.getprefb("autoClickMenuOption", true);
        return ret;
    }

    // set flags for clickWItem and ZeeClickGobManager.gobClick
    public static void scheduleClickPetalOnce(String name) {
        clickPetal = true;
        clickPetalName = name;
    }

    public static void resetClickPetal() {//TODO check if still needed
        clickPetal = false;
        clickPetalName = "";
    }

    private boolean isGobBigAnimal(){
        return gobNameEndsWith(
            gobName,
            "/stallion,/mare,/foal,/hog,/sow,/piglet,"
            +"/goat,/nanny,/kid,/sheep,/lamb,/cattle,/calf,"
            +"/wildhorse,/aurochs,/mouflon,/wildgoat,"
            +"/adder,/badger,/bear,/boar,/beaver,/deer,/reindeer,/fox,"
            +"/seal,/otter,/caveangler,/boreworm,/caverat,"
            +"/lynx,/mammoth,/moose,/troll,/walrus,/wolf,/wolverine"
        );
    }

    private boolean isGobHorse() {
        return gobNameEndsWith(gobName, "stallion,mare,horse");
    }

    private boolean isGobFireSource() {
        return gobNameEndsWith(gobName,"brazier,pow,snowlantern");
    }

    /**
     * Itemact with gob, to fill trough with item in hand for example
     * @param mod 1 = shift, 2 = ctrl, 4 = alt  (3 = ctrl+shift ?)
     */
    public void gobItemAct(int mod) {
        ZeeConfig.gameUI.map.wdgmsg("itemact", Coord.z, gob.rc.floor(OCache.posres), mod, 0, (int) gob.id, gob.rc.floor(OCache.posres), 0, -1);
    }

    public static void gobItemAct(Gob g, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("itemact", Coord.z, g.rc.floor(OCache.posres), mod, 0, (int) g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    public void gobClick(int btn) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), gob.rc.floor(OCache.posres), btn, 0, 0, (int)gob.id, gob.rc.floor(OCache.posres), 0, -1);
    }

    public void gobClick(int btn, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), gob.rc.floor(OCache.posres), btn, mod, 0, (int)gob.id, gob.rc.floor(OCache.posres), 0, -1);
    }

    public static void gobClick(Gob g, int btn) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), g.rc.floor(OCache.posres), btn, 0, 0, (int)g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    public static void gobClick(Gob g, int btn, int mod) {
        ZeeConfig.gameUI.map.wdgmsg("click", ZeeConfig.getCenterScreenCoord(), g.rc.floor(OCache.posres), btn, mod, 0, (int)g.id, g.rc.floor(OCache.posres), 0, -1);
    }

    public static double distanceCoordGob(Coord2d c, Gob gob) {
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
}
