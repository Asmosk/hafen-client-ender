# Zeecow hafen-client

Client focusing on small helper tasks, many using mouse middle button, and other stuff:

Controls
--------
- flowermenu also works on mouse up
- minimap drags with button 3
- right click crop and drag also starts harvest area selection (slow)
- keys up/down controls volume
- keys left/right controls ambient light (home key resets light)
- quick zoom with PageUp/PageDown, or Rclick player and drag
- camera drag also works by holding left-click (move player + drag camera)   
- shift+c alternates cams ortho/free
- shift+h toggle hitboxes
- ctrl+h toggle trees/palisegs/crops
- mousewheel scroll transfer items directly (no shift required)
- Ctrl+click to confirm actions (Empty, Swill, Slaughter, etc)
- Ctrl confirm throwing axe/spear
- Ctrl+Rclick confirm remove shortcut
- Alt+click to drop holding item
- shift+tab toggles between belt, creel and basket (probably outdated)
- right-click Stoat's icon to aggro on max speed (option available)
- pickup key "q", shift+q pickup all, ctrl+q pickup window
- drink key '
- auto-dismount horse when clicking workstations, houses, auto-mount too (rope required)
- Icon Settings: shift+space toggles notify checkbox
- right-click-hold Search Actions Button to start a hover menu



Midclick interactions (probabaly incomplete)
-------------------------------------------

Midclick Ground
- while holding item will move to location (simulates ctrl+click)
- while inspecting icon active, will msg tile name
- place stockpile and all items
- dig multiple ballclay if cursor dig
- while driving plow will queue coords 

Long Midclick Ground (LMC)
- while mounting horse will try to dismount
- while carrying wheelbarrow will try to(dismount and) unload stockpile at ground
- while coracle equipped, LMC water will try to drop and mount
- while coracle mounted, LMC ground will try to dismount and equip
- disembark dugout, rowboat, coracle, kicksled
- activate snow clear area
- show flowermenu for
  - inspect water tile using inventory woodencup
  - build road
  - actions fish, dig, mine

Midclick Ground Object
- inspect object quality and show text
- harvest 2 dreams from dreamcatch objects closeby
- Giddyup! if obj is horse
- lightup torch on firesource obj
- open "Cargo" on Knarr/Snekkja obj
- toggle barrels labels
- toggle mine support radius
- toggle aggressive animal obj radius
- while holding item will try to ctrl+click obj(containers,stockpiles)
- open cauldron
- place lifted treelog next to clicked treelog(perpendicular only)
- while crafting rope, midclicking fibre piles will get fibres and "Craft All" again
- midclick barterstand to open search window
- midclick smelter holding bucket to collect all quicksilver
- midclick stockpile while making block/board/sand will pile inv items and try acquire more
- midclick animal holding clover to follow and feed it when close enough

Long Midclick Ground Object
- show context menu for various objs (details below)
- lift up obj
- take all from stockpile obj
- click "Man the helm" on Knarr/Snekkja obj
- while holding item will try to store all on barrel obj
- add/remove tree obj to "Remove all trees" ordered queue
- add/remove treelog obj to "Destroy all" ordered queue
- while carrying wheelbarrow will try to(dismount and) unload at stockpile/container
- while driving wheelbarrow will lift it up and open gate obj
- while driving wheelbarrow will lift it up and place on cart obj
- while carrying/driving wheelbarrow will mount horse obj and lift wheelbarrow again
- show window for area piler
- pile all clay if clicked pile with cursor dig
- longmidclick stockpile while making block/board/sand will try pile items until pile full or object consumed
- put out cauldron

Midclick Window Item
- sort-transfer item ascending(?)
- pick-up all
- quick-equip from belt
- seed-planting cursor
- while holding cheese-curd will fill up cheese-tray item
- undo stack item (ctrl+shift+Rclick) if transfer not available
- create single stack when hold and midclick same item type

Long Midclick Window Item
- sort-transfer item descending(?)
- show context menu for various items (details below)
- equip fishing hook/lure and recast
- equip two sack items from belt
- undo multiple stack items if transfer not available
- create multiple stacks when hold and midclick same item type

LongMidClick Flowemenu for Ground Objects
- auto butch dead animal objs
- start seed farmer for crop objs: harvest, store and replant seeds (no pathfinding)
- harvest area for crop objs: activate harvest icon (shift+click)
- add 4 branches to oven obj
- add 9/12 coal to smelter obj
- remove tree and stump
- remove trellis plant(s)
- destroy multiple treelogs (if bonesaw is equipped)
- player context menu
  - switch char
  - mine tiles window
  - test coords
  - emote wave, laugh
  - clear gob texts
- tamed animal context menu
  - open cattle roster
  - memorize area
- wildbeehive shows build pyre
- lightable objects shows craft firebrand, pyrite spark
- crucible smelts

LongMidClick Flowemenu for Window Items
- auto butch dead animal items
- kill all cocoon items
- feast all food items(requires table)
- transfer sort items (asc/desc)


Random Features
---------------
- solid colors option: minimap, terrain
- search actions works globally, instead of current menu
- sort actions by most used craft items (size limited for now)
- craft window's search ingredients button
- window buttons auto-organize duplicates, auto-hoverhide
- simple window and buttons
- compact equip window, auto toggle equip window
- shorter cattle roster window
- reposition map window when compacted/expanded
- basic inventory item counter
- tamed animal name generator, using animal's stats
- auto-pile helper for mulberry leaves, woodblocks, boards, coal, ground stones
- seed-farmer manager: no pathfinding, buggy (ctrlLongMidClick crop)
- cook manager: auto cook pepper and refill cauldron until barrel is empty(no pathfinding)
- icon list categs
- shaped icons options
- label trasfering barrels
- Window fuel buttons for Oven, Kiln, Smelter
- free gob placement option
- auto toggle gridlines
- Quick Options window show 3 latest used options, and latest flowermenu
- default icon sound to "Bell 2" if empty
- inspect cursor tooltip containing gob/tile/minimap info
- Barter Stand "allow mid-click auto-buy" checkbox
- Barter Stand button return branches to closest wooden chest
- Audio msg mute list
- lift vehicle before travel hearth
- midclick mark expands minimap and select mark
- new mark zooms in map
- area piler: create piles around selected area (long-midclick item)
- main inventory checkboxes for midclick transfer modes: asc, desc, one, ql
- gob monitor: highlight, play audio, text to speech (cmd festival)
- Feasting log window, count gains
- Option "Fish Moon XP" show text over calendar
- Scroll text entry fuel for kiln, oven, smelter
- hover menu on search actions button (hold right-click)
- auto-label gob Barrel/Cistern/Demijohn when it's window is opened
- auto craft helper: cloth linen/hemp, rope, bug collection
  - use closest fibre pile until inv full
- auto press wine from inventtory
  - switch seeds and press until it stops
- (unfinished) Lag Camera (:cam lag)
- autodrop mined items, seeds, soil
- auto click menu options
- auto drink
- sort transfer with Alt+Rclick or Alt+Lclick (no gemstone yet)
- Auto-hearth
- replaces arrow cursor with system's default
- show claims by default
- optional friend online notification
- craft window numbers, history, search ingredients
- highlight damaged gobs more
- highlight finished crops, growing trees, gobs and categories
- zoom extended for ortho cam
- mini trees
- hide smoke and some animations
- keybelt shortcuts navigation
- reposition rightmost windows horiz. when game resize
- CheeseTray recipe show progress of recently opened CheeseRack 
- try auto pick irrlight and craft again (craft window checkbox)
 