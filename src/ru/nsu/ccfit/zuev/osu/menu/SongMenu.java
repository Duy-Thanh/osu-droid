package ru.nsu.ccfit.zuev.osu.menu;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.edlplan.ext.EdExtensionHelper;
import com.edlplan.favorite.FavoriteLibrary;
import com.edlplan.replay.OdrDatabase;
import com.edlplan.ui.fragment.PropsMenuFragment;
import com.edlplan.ui.fragment.ScoreMenuFragment;
import com.reco1l.api.ibancho.RoomAPI;
import com.reco1l.legacy.Multiplayer;
import com.reco1l.legacy.ui.multiplayer.RoomScene;
import com.reco1l.framework.lang.execution.Async;
import com.rian.difficultycalculator.attributes.DifficultyAttributes;
import com.rian.difficultycalculator.calculator.DifficultyCalculationParameters;

import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.engine.handler.IUpdateHandler;
import org.anddev.andengine.entity.Entity;
import org.anddev.andengine.entity.primitive.Rectangle;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.entity.scene.background.ColorBackground;
import org.anddev.andengine.entity.scene.background.SpriteBackground;
import org.anddev.andengine.entity.sprite.Sprite;
import org.anddev.andengine.entity.text.ChangeableText;
import org.anddev.andengine.entity.text.Text;
import org.anddev.andengine.input.touch.TouchEvent;
import org.anddev.andengine.opengl.texture.region.TextureRegion;
import org.anddev.andengine.util.Debug;
import org.anddev.andengine.util.HorizontalAlign;
import org.anddev.andengine.util.MathUtils;

import java.text.SimpleDateFormat;
import java.util.*;

import org.jetbrains.annotations.Nullable;
import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.audio.Status;
import ru.nsu.ccfit.zuev.osu.BeatmapInfo;
import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.LibraryManager;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.TrackInfo;
import ru.nsu.ccfit.zuev.osu.Utils;
import ru.nsu.ccfit.zuev.osu.async.AsyncTask;
import ru.nsu.ccfit.zuev.osu.async.SyncTaskManager;
import ru.nsu.ccfit.zuev.osu.beatmap.BeatmapData;
import ru.nsu.ccfit.zuev.osu.beatmap.parser.BeatmapParser;
import ru.nsu.ccfit.zuev.osu.game.GameHelper;
import ru.nsu.ccfit.zuev.osu.game.GameScene;
import ru.nsu.ccfit.zuev.osu.game.mods.GameMod;
import ru.nsu.ccfit.zuev.osu.helper.AnimSprite;
import ru.nsu.ccfit.zuev.osu.helper.BeatmapDifficultyCalculator;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager.OnlineManagerException;
import ru.nsu.ccfit.zuev.osu.online.OnlinePanel;
import ru.nsu.ccfit.zuev.osu.online.OnlineScoring;
import ru.nsu.ccfit.zuev.osu.scoring.Replay;
import ru.nsu.ccfit.zuev.osu.scoring.ScoreLibrary;
import ru.nsu.ccfit.zuev.osu.scoring.ScoringScene;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.osuplus.R;
import ru.nsu.ccfit.zuev.skins.OsuSkin;
import ru.nsu.ccfit.zuev.skins.SkinLayout;

public class SongMenu implements IUpdateHandler, MenuItemListener,
        IScrollBarListener {
    private final static Boolean musicMutex = true;
    private final Boolean backgroundMutex = true;
    public Scene scene;
    public Entity frontLayer = new Entity();
    SortOrder sortOrder = SortOrder.Title;
    private Engine engine;
    public GameScene game;
    private ScoringScene scoreScene;
    private float camY = 0;
    private float velocityY;
    private Activity context;
    private Entity backLayer = new Entity();
    private ArrayList<MenuItem> items = new ArrayList<>();
    private MenuItem selectedItem = null;
    private TrackInfo selectedTrack;
    private Sprite bg = null;
    private Boolean bgLoaded = false;
    private String bgName = "";
    private ScoreBoard board;
    private Float touchY = null;
    private String filterText = "";
    private boolean favsOnly = false;
    private Set<String> limitC;
    private float secondsSinceLastSelect = 0;
    private float maxY = 100500;
    private int pointerId = -1;
    private float initalY = -1;
    private float secPassed = 0, tapTime;
    private Sprite backButton = null;
    private ScrollBar scrollbar;
    private ChangeableText trackInfo, mapper, beatmapInfo, beatmapInfo2, dimensionInfo;
    private boolean isSelectComplete = true;
    private AnimSprite scoringSwitcher = null;
    private GroupType groupType = GroupType.MapSet;

    private Timer previousSelectionTimer;
    private final long previousSelectionInterval = 1000;
    private boolean previousSelectionPerformed;
    private final LinkedList<MenuItem> previousSelectedItems = new LinkedList<>();

    public SongMenu() {
    }

    public static void stopMusicStatic() {
        synchronized (musicMutex) {
            if (GlobalManager.getInstance().getSongService() != null) {
                GlobalManager.getInstance().getSongService().stop();
            }
        }
    }

    public void setScoringScene(final ScoringScene ss) {
        scoreScene = ss;
    }

    @Nullable
    public ArrayList<ScoreBoardItem> getBoard() {
        return board.getScoreBoardItems();
    }

    public boolean isBoardOnline() {
        return board.isShowOnlineScores();
    }

    public ArrayList<MenuItem> getMenuItems() {
        return items;
    }

    public void init(final Activity context, final Engine engine,
                     final GameScene pGame) {
        this.engine = engine;
        game = pGame;
        this.context = context;
    }

    public void loadFilter(IFilterMenu filterMenu) {
        setFilter(filterMenu.getFilter(), filterMenu.getOrder(), filterMenu.isFavoritesOnly(),
                filterMenu.getFavoriteFolder() == null ? null : FavoriteLibrary.get().getMaps(filterMenu.getFavoriteFolder()));
    }

    public void reload() {
        frontLayer = new Entity();
        backLayer = new Entity();
        scene.unregisterUpdateHandler(this);
        scene.setTouchAreaBindingEnabled(false);
        load();
        GlobalManager.getInstance().getGameScene().setOldScene(scene);
    }

    public synchronized void load() {
        scene = new Scene();
        camY = 0;
        velocityY = 0;
        selectedItem = null;
        items = new ArrayList<>();
        selectedTrack = null;
        bgLoaded = true;
        SongMenuPool.getInstance().init();
        FilterMenu.getInstance().loadConfig(context);

        // Preventing ModMenu to reload mod set
        if (!Multiplayer.isMultiplayer)
            ModMenu.getInstance().reload();

        bindDataBaseChangedListener();

        scene.attachChild(backLayer);
        scene.attachChild(frontLayer);

        final TextureRegion tex = ResourceManager.getInstance().getTexture("menu-background");
        float height = tex.getHeight();
        height *= Config.getRES_WIDTH() / (float) tex.getWidth();
        final Sprite bg = new Sprite(0, (Config.getRES_HEIGHT() - height) / 2,
                Config.getRES_WIDTH(), height, tex);
        scene.setBackground(new SpriteBackground(bg));

        final Rectangle bgDimRect = new Rectangle(0, 0, Config.getRES_WIDTH(), Config.getRES_HEIGHT());
        bgDimRect.setColor(0, 0, 0, 0.2f);
        backLayer.attachChild(bgDimRect);

        board = new ScoreBoard(scene, backLayer, this);

        float oy = 10;
        for (final BeatmapInfo i : LibraryManager.INSTANCE.getLibrary()) {
            final MenuItem item = new MenuItem(this, i);
            items.add(item);
            item.attachToScene(scene, backLayer);
            oy += item.getHeight();
        }
        sortOrder = SortOrder.Title;
        sort();

        if (items.size() == 0) {
            final Text text = new Text(0, 0, ResourceManager.getInstance()
                    .getFont("CaptionFont"), "There are no songs in library, try using chimu.moe",
                    HorizontalAlign.CENTER);
            text.setPosition(Config.getRES_WIDTH() / 2f - text.getWidth() / 2,
                    Config.getRES_HEIGHT() / 2f - text.getHeight() / 2);
            text.setScale(1.5f);
            text.setColor(0, 0, 0);
            scene.attachChild(text);
            return;
        }

        scene.setOnSceneTouchListener((pScene, evt) -> {
            if (evt.getX() < Config.getRES_WIDTH() / 5f * 2) {
                return false;
            }
            switch (evt.getAction()) {
                case (TouchEvent.ACTION_DOWN):
                    velocityY = 0;
                    touchY = evt.getY();
                    pointerId = evt.getPointerID();
                    tapTime = secPassed;
                    initalY = touchY;
                    break;
                case (TouchEvent.ACTION_MOVE):
                    if (pointerId != -1 && pointerId != evt.getPointerID()) {
                        break;
                    }
                    if (initalY == -1) {
                        velocityY = 0;
                        touchY = evt.getY();
                        initalY = touchY;
                        tapTime = secPassed;
                        pointerId = evt.getPointerID();
                    }
                    final float dy = evt.getY() - touchY;

                    camY -= dy;
                    touchY = evt.getY();
                    if (camY <= -Config.getRES_HEIGHT() / 2f) {
                        camY = -Config.getRES_HEIGHT() / 2f;
                        velocityY = 0;
                    } else if (camY >= maxY) {
                        camY = maxY;
                        velocityY = 0;
                    }

                    // velocityY = -3f * dy;
                    break;
                default: {
                    if (pointerId != -1 && pointerId != evt.getPointerID()) {
                        break;
                    }
                    touchY = null;
                    if (secPassed - tapTime < 0.001f || initalY == -1) {
                        velocityY = 0;
                    } else {
                        velocityY = (initalY - evt.getY())
                                / (secPassed - tapTime);
                    }
                    pointerId = -1;
                    initalY = -1;

                }
                break;
            }
            return true;
        });

        scene.registerUpdateHandler(this);
        scene.setTouchAreaBindingEnabled(true);

        scrollbar = new ScrollBar(scene);

        final TextureRegion songSelectTopTexture = ResourceManager.getInstance().getTexture("songselect-top");
        final Sprite songSelectTop = new Sprite(0, 0, songSelectTopTexture);
        songSelectTop.setSize(songSelectTopTexture.getWidth() * songSelectTopTexture.getHeight() / 184f, 184);
        songSelectTop.setPosition(-1640, songSelectTop.getY());
        songSelectTop.setAlpha(0.6f);
        frontLayer.attachChild(songSelectTop);

        trackInfo = new ChangeableText(Utils.toRes(70), Utils.toRes(2),
                ResourceManager.getInstance().getFont("font"), "title", 1024);
        frontLayer.attachChild(trackInfo);

        mapper = new ChangeableText(Utils.toRes(70), trackInfo.getY() + trackInfo.getHeight() + Utils.toRes(2),
                ResourceManager.getInstance().getFont("middleFont"), "mapper", 1024);
        frontLayer.attachChild(mapper);

        beatmapInfo = new ChangeableText(Utils.toRes(4), mapper.getY() + mapper.getHeight() + Utils.toRes(2),
                ResourceManager.getInstance().getFont("middleFont"), "beatmapInfo", 1024);
        frontLayer.attachChild(beatmapInfo);

        beatmapInfo2 = new ChangeableText(Utils.toRes(4), beatmapInfo.getY() + beatmapInfo.getHeight() + Utils.toRes(2),
                ResourceManager.getInstance().getFont("middleFont"), "beatmapInfo2", 1024);
        frontLayer.attachChild(beatmapInfo2);

        dimensionInfo = new ChangeableText(Utils.toRes(4), beatmapInfo2.getY() + beatmapInfo2.getHeight() + Utils.toRes(2),
                ResourceManager.getInstance().getFont("smallFont"), "dimensionInfo", 1024);
        frontLayer.attachChild(dimensionInfo);


        SkinLayout layoutBackButton = OsuSkin.get().getLayout("BackButton");
        SkinLayout layoutMods = null;

        if (!Multiplayer.isMultiplayer)
            layoutMods = OsuSkin.get().getLayout("ModsButton");

        SkinLayout layoutOptions = OsuSkin.get().getLayout("OptionsButton");
        SkinLayout layoutRandom = OsuSkin.get().getLayout("RandomButton");

        if (ResourceManager.getInstance().isTextureLoaded("menu-back-0")) {
            List<String> loadedBackTextures = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                if (ResourceManager.getInstance().isTextureLoaded("menu-back-" + i))
                    loadedBackTextures.add("menu-back-" + i);
            }
            backButton = new AnimSprite(0, 0, loadedBackTextures.size(), loadedBackTextures.toArray(new String[0])) {
                boolean moved = false;
                float dx = 0, dy = 0;
                boolean scaleWhenHold = true;

                {
                    if (layoutBackButton != null) {
                        scaleWhenHold = layoutBackButton.property.optBoolean("scaleWhenHold", true);
                    }
                }

                @Override
                public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
                                             final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
                    if (pSceneTouchEvent.isActionDown()) {
                        if (scaleWhenHold) backButton.setScale(1.25f);
                        moved = false;
                        dx = pTouchAreaLocalX;
                        dy = pTouchAreaLocalY;
                        BassSoundProvider playSnd = ResourceManager.getInstance().getSound("menuback");
                        if (playSnd != null) {
                            playSnd.play();
                        }
                        return true;
                    }
                    if (pSceneTouchEvent.isActionUp()) {
                        // back
                        if (selectedTrack == null) {
                            return true;
                        }
                        if (!moved) {
                            backButton.setScale(1f);
                            back();
                        }
                        return true;
                    }
                    if (pSceneTouchEvent.isActionOutside()
                            || pSceneTouchEvent.isActionMove()
                            && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                            pTouchAreaLocalY) > 50)) {
                        backButton.setScale(1f);
                        moved = true;
                    }
                    return false;
                }
            };
        } else {
            backButton = new Sprite(0, 0, ResourceManager.getInstance().getTexture("menu-back")) {
                boolean moved = false;
                float dx = 0, dy = 0;
                boolean scaleWhenHold = true;

                {
                    if (layoutBackButton != null) {
                        scaleWhenHold = layoutBackButton.property.optBoolean("scaleWhenHold", true);
                    }
                }

                @Override
                public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
                                             final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
                    if (pSceneTouchEvent.isActionDown()) {
                        if (scaleWhenHold) backButton.setScale(1.25f);
                        moved = false;
                        dx = pTouchAreaLocalX;
                        dy = pTouchAreaLocalY;
                        BassSoundProvider playSnd = ResourceManager.getInstance().getSound("menuback");
                        if (playSnd != null) {
                            playSnd.play();
                        }
                        return true;
                    }
                    if (pSceneTouchEvent.isActionUp()) {
                        // back
                        if (selectedTrack == null) {
                            return true;
                        }
                        if (!moved) {
                            backButton.setScale(1f);
                            back();
                        }
                        return true;
                    }
                    if (pSceneTouchEvent.isActionOutside()
                            || pSceneTouchEvent.isActionMove()
                            && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                            pTouchAreaLocalY) > 50)) {
                        backButton.setScale(1f);
                        moved = true;
                    }
                    return false;
                }
            };
        }

        AnimSprite modSelection = null;

        if (!Multiplayer.isMultiplayer)
            modSelection = new AnimSprite(0, 0, 0,
                    "selection-mods", "selection-mods-over") {
                boolean moved = false;
                private float dx = 0, dy = 0;


                @Override
                public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
                                             final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
                    if (pSceneTouchEvent.isActionDown()) {
                        setFrame(1);
                        moved = false;
                        dx = pTouchAreaLocalX;
                        dy = pTouchAreaLocalY;
                        return true;
                    }
                    if (pSceneTouchEvent.isActionUp()) {
                        setFrame(0);
                        if (!moved) {
                            velocityY = 0;
                            ModMenu.getInstance().show(scene, selectedTrack);
                        }
                        return true;
                    }
                    if (pSceneTouchEvent.isActionOutside()
                            || pSceneTouchEvent.isActionMove()
                            && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                            pTouchAreaLocalY) > 50)) {
                        moved = true;
                        setFrame(0);
                    }
                    return false;
                }
            };

        final AnimSprite optionSelection = new AnimSprite(0, 0, 0,
                "selection-options", "selection-options-over") {
            boolean moved = false;
            private float dx = 0, dy = 0;


            @Override
            public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
                                         final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
                if (pSceneTouchEvent.isActionDown()) {
                    setFrame(1);
                    moved = false;
                    dx = pTouchAreaLocalX;
                    dy = pTouchAreaLocalY;
                    return true;
                }
                if (pSceneTouchEvent.isActionUp()) {

                    setFrame(0);
                    if (!moved) {
                        velocityY = 0;

                        FilterMenu.getInstance().showMenu(SongMenu.this);
                    }
                    return true;
                }
                if (pSceneTouchEvent.isActionOutside()
                        || pSceneTouchEvent.isActionMove()
                        && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                        pTouchAreaLocalY) > 50)) {
                    moved = true;
                    setFrame(0);
                }
                return false;
            }
        };

        final AnimSprite randomMap = new AnimSprite(0, 0, 0,
                "selection-random", "selection-random-over") {
            boolean moved = false;
            private float dx = 0, dy = 0;

            @Override
            public boolean onAreaTouched(final TouchEvent pSceneTouchEvent,
                                         final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
                if (pSceneTouchEvent.isActionDown()) {
                    setFrame(1);
                    moved = false;
                    dx = pTouchAreaLocalX;
                    dy = pTouchAreaLocalY;

                    if (previousSelectionTimer != null) {
                        previousSelectionTimer.cancel();
                    }

                    previousSelectionTimer = new Timer();
                    previousSelectionTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            if (!isSelectComplete) {
                                return;
                            }

                            MenuItem previousItem = previousSelectedItems.pollLast();
                            while (previousItem != null && previousItem.isDeleted()) {
                                previousItem = previousSelectedItems.pollLast();
                            }

                            if (previousItem == null) {
                                cancel();
                                return;
                            }

                            previousSelectionPerformed = true;

                            ResourceManager.getInstance().getSound("menuclick")
                                    .play();
                            previousItem.select(true, true);
                        }

                        @Override
                        public boolean cancel() {
                            previousSelectionTimer = null;
                            return super.cancel();
                        }
                    }, previousSelectionInterval, previousSelectionInterval);

                    previousSelectionPerformed = false;
                    return true;
                }
                if (pSceneTouchEvent.isActionUp()) {
                    setFrame(0);

                    if (previousSelectionTimer != null) {
                        previousSelectionTimer.cancel();
                    }

                    if (!isSelectComplete) {
                        return true;
                    }

                    if (!moved && !previousSelectionPerformed) {
                        velocityY = 0;
                        if (items.size() <= 1) {
                            return true;
                        }
                        int rnd = MathUtils.random(0, items.size() - 1);
                        int index = 0;
                        while (rnd > 0) {
                            rnd--;
                            int oldIndex = index;
                            do {
                                index = (index + 1) % items.size();
                                if (index == oldIndex)
                                    return true;
                            } while (!items.get(index).isVisible());
                        }
                        if (!items.get(index).isVisible()) {
                            return true;
                        }
                        if (selectedItem == items.get(index)) {
                            return true;
                        }
                        ResourceManager.getInstance().getSound("menuclick")
                                .play();
                        items.get(index).select(true, true);
                    }

                    previousSelectionPerformed = false;
                    return true;
                }
                if (pSceneTouchEvent.isActionOutside()
                        || pSceneTouchEvent.isActionMove()
                        && (MathUtils.distance(dx, dy, pTouchAreaLocalX,
                        pTouchAreaLocalY) > 50)) {
                    moved = true;
                    setFrame(0);

                    if (pSceneTouchEvent.isActionOutside() && previousSelectionTimer != null) {
                        previousSelectionTimer.cancel();
                    }
                }
                return false;
            }
        };

        if (modSelection != null)
            modSelection.setScale(1.5f);

        optionSelection.setScale(1.5f);
        randomMap.setScale(1.5f);
        if (OsuSkin.get().isUseNewLayout()) {
            if (layoutBackButton != null) {
                layoutBackButton.baseApply(backButton);
            }
            if (layoutMods != null && modSelection != null) {
                layoutMods.baseApply(modSelection);
            }
            if (layoutOptions != null) {
                layoutOptions.baseApply(optionSelection);
            }
            if (layoutRandom != null) {
                layoutRandom.baseApply(randomMap);
            }
            backButton.setPosition(0, Config.getRES_HEIGHT() - backButton.getHeightScaled());

            if (modSelection != null) {
                modSelection.setPosition(backButton.getX() + backButton.getWidth(),
                                         Config.getRES_HEIGHT() - modSelection.getHeightScaled());
                optionSelection.setPosition(
                        modSelection.getX() + modSelection.getWidthScaled(),
                        Config.getRES_HEIGHT() - optionSelection.getHeightScaled());
            } else {
                optionSelection.setPosition(backButton.getX() + backButton.getWidth(),
                                         Config.getRES_HEIGHT() - optionSelection.getHeightScaled());
            }
            randomMap.setPosition(
                    optionSelection.getX() + optionSelection.getWidthScaled(),
                    Config.getRES_HEIGHT() - randomMap.getHeightScaled());
        } else {
            backButton.setPosition(0, Config.getRES_HEIGHT() - backButton.getHeight());

            if (modSelection != null) {
                modSelection.setPosition(backButton.getX() + backButton.getWidth(),
                                         Config.getRES_HEIGHT() - Utils.toRes(90));
                optionSelection.setPosition(
                        modSelection.getX() + modSelection.getWidthScaled(),
                        Config.getRES_HEIGHT() - Utils.toRes(90));
            } else {
                optionSelection.setPosition(backButton.getX() + backButton.getWidth(),
                                         Config.getRES_HEIGHT() - Utils.toRes(90));
            }
            randomMap.setPosition(
                    optionSelection.getX() + optionSelection.getWidthScaled(),
                    Config.getRES_HEIGHT() - Utils.toRes(90));
        }


        frontLayer.attachChild(backButton);
        scene.registerTouchArea(backButton);

        if (modSelection != null)
        {
            frontLayer.attachChild(modSelection);
            scene.registerTouchArea(modSelection);
        }
        frontLayer.attachChild(optionSelection);
        scene.registerTouchArea(optionSelection);
        frontLayer.attachChild(randomMap);
        scene.registerTouchArea(randomMap);

        if (OnlineScoring.getInstance().createSecondPanel() != null) {
            OnlinePanel panel = OnlineScoring.getInstance().getSecondPanel();
            panel.detachSelf();
            panel.setPosition(randomMap.getX() + randomMap.getWidthScaled() - 18, Config.getRES_HEIGHT() - Utils.toRes(110));
            OnlineScoring.getInstance().loadAvatar(false);
            frontLayer.attachChild(panel);


            scoringSwitcher = new AnimSprite(Utils.toRes(5), Utils.toRes(10), 0, "ranking_enabled", "ranking_disabled") {
                @Override
                public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
                                             float pTouchAreaLocalX, float pTouchAreaLocalY) {
                    if (!pSceneTouchEvent.isActionDown()) return false;
                    toggleScoringSwitcher();
                    return true;
                }
            };
            scoringSwitcher.setFrame(1);
            scoringSwitcher.setPosition(10, 10);
            scene.registerTouchArea(scoringSwitcher);
            frontLayer.attachChild(scoringSwitcher);
        }
    }

    public void toggleScoringSwitcher() {
        if (board.isShowOnlineScores()) {
            board.setShowOnlineScores(false);
            board.init(selectedTrack);
            scoringSwitcher.setFrame(1);
            updateInfo(selectedTrack);
        } else if (OnlineManager.getInstance().isStayOnline()) {
            board.setShowOnlineScores(true);
            board.init(selectedTrack);
            setRank();
        }
    }

    public void setRank() {
        if (!board.isShowOnlineScores()) {
            if (scoringSwitcher != null) {
                scoringSwitcher.setFrame(1);
            }
            updateInfo(selectedTrack);
            return;
        }
        scoringSwitcher.setFrame(0);
    }

    public Scene getScene() {
        return scene;
    }

    public void show() {
        engine.setScene(scene);
    }

    public void setFilter(final String filter, final SortOrder order,
                          final boolean favsOnly, Set<String> limit) {
        String oldTrackFileName = "";
        if (selectedTrack != null) {
            oldTrackFileName = selectedTrack.getFilename();
        }
        if (!order.equals(sortOrder)) {
            sortOrder = order;
            tryReloadMenuItems(sortOrder);
            sort();
            reSelectItem(oldTrackFileName);
        }
        if (filter == null || filterText.equals(filter)) {
            if (favsOnly == this.favsOnly && limitC == limit) {
                return;
            }
        }
        limitC = limit;
        filterText = filter;
        camY = 0;
        velocityY = 0;
        final String lowerFilter = filter.toLowerCase();
        for (final MenuItem item : items) {
            item.applyFilter(lowerFilter, favsOnly, limit);
        }
        if (favsOnly != this.favsOnly) {
            this.favsOnly = favsOnly;
        } else {
            reSelectItem(oldTrackFileName);
        }
        if (selectedItem != null && !selectedItem.isVisible()) {
            selectedItem = null;
            selectedTrack = null;
        }
        System.gc();
    }

    public void sort() {
        if (!sortOrder.equals(FilterMenu.getInstance().getOrder())) {
            sortOrder = FilterMenu.getInstance().getOrder();
        }
        Collections.sort(items, (i1, i2) -> {
            String s1;
            String s2;
            switch (sortOrder) {
                case Artist:
                    s1 = i1.getBeatmap().getArtist();
                    s2 = i2.getBeatmap().getArtist();
                    break;
                case Creator:
                    s1 = i1.getBeatmap().getCreator();
                    s2 = i2.getBeatmap().getCreator();
                    break;
                case Date:
                    final Long int1 = i1.getBeatmap().getDate();
                    final Long int2 = i2.getBeatmap().getDate();
                    return int2.compareTo(int1);
                case Bpm:
                    final float bpm1 = i1.getFirstTrack().getBpmMax();
                    final float bpm2 = i2.getFirstTrack().getBpmMax();
                    return Float.compare(bpm2, bpm1);
                case Stars:
                    final float float1 = i1.getFirstTrack().getDifficulty();
                    final float float2 = i2.getFirstTrack().getDifficulty();
                    return Float.compare(float2, float1);
                case Length:
                    final Long length1 = i1.getFirstTrack().getMusicLength();
                    final Long length2 = i2.getFirstTrack().getMusicLength();
                    return length2.compareTo(length1);
                default:
                    s1 = i1.getBeatmap().getTitle();
                    s2 = i2.getBeatmap().getTitle();
            }

            return s1.compareToIgnoreCase(s2);
        });
    }

    public void onUpdate(final float pSecondsElapsed) {
        secPassed += pSecondsElapsed;
        increaseVolume();
        increaseBackgroundLuminance(pSecondsElapsed);

        secondsSinceLastSelect += pSecondsElapsed;
        float oy = -camY;
        for (final MenuItem item : items) {
            final float cy = oy + Config.getRES_HEIGHT() / 2f + item.getHeight()
                    / 2;
            float ox = Config.getRES_WIDTH() / 1.85f + 200 * (float) Math.abs(Math.cos(cy * Math.PI
                    / (Config.getRES_HEIGHT() * 2)));
            ox = Utils.toRes(ox);
            item.setPos(ox, oy);
            oy += item.getHeight();
        }
        oy += camY;
        camY += velocityY * pSecondsElapsed;
        maxY = oy - Config.getRES_HEIGHT() / 2f;
        if (camY <= -Config.getRES_HEIGHT() / 2f && velocityY < 0
                || camY >= maxY && velocityY > 0) {
            camY -= velocityY * pSecondsElapsed;
            velocityY = 0;
        }
        if (Math.abs(velocityY) > Utils.toRes(1000) * pSecondsElapsed) {
            velocityY -= Utils.toRes(1000) * pSecondsElapsed
                    * Math.signum(velocityY);
        } else {
            velocityY = 0;
        }

        expandSelectedItem(pSecondsElapsed);

        updateScrollbar(camY + Config.getRES_HEIGHT() / 2f, oy);
    }

    public void increaseVolume() {
        if (GlobalManager.getInstance().getSongService() != null) {
            synchronized (musicMutex) {
                if (GlobalManager.getInstance().getSongService() != null && GlobalManager.getInstance().getSongService().getStatus() == Status.PLAYING && GlobalManager.getInstance().getSongService().getVolume() < Config.getBgmVolume()) {
                    float vol = Math.min(1, GlobalManager.getInstance().getSongService().getVolume() + 0.01f);
                    GlobalManager.getInstance().getSongService().setVolume(vol);
                }
            }
        }
    }

    public void increaseBackgroundLuminance(final float pSecondsElapsed) {
        if (bg != null) {
            synchronized (backgroundMutex) {
                if (bg != null && bg.getRed() < 1) {
                    final float col = Math.min(1, bg.getRed() + pSecondsElapsed);
                    bg.setColor(col, col, col);
                }
            }
        }
    }

    public void expandSelectedItem(float pSecondsElapsed) {
        if (selectedItem != null) {
            if (selectedItem.percentAppeared < 1) {
                selectedItem.percentAppeared += 2 * pSecondsElapsed;
            } else {
                selectedItem.percentAppeared = 1;
            }
            selectedItem.update(pSecondsElapsed);
        }
    }

    public void updateScrollbar(final float vy, final float maxy) {
        scrollbar.setPosition(vy, maxy);
        scrollbar.setVisible(Math.abs(velocityY) > Utils.toRes(500));
    }

    public void reset() {
    }

    public void select(final MenuItem item) {
        secondsSinceLastSelect = 0;
        if (selectedItem != null) {
            selectedItem.deselect();

            if (!previousSelectionPerformed) {
                while (previousSelectedItems.size() >= 10) {
                    previousSelectedItems.pollFirst();
                }
                previousSelectedItems.addLast(selectedItem);
            }
        }

        selectedItem = item;
        velocityY = 0;
        selectedTrack = null;
        float height = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == selectedItem) {
                break;
            }
            height += items.get(i).getInitialHeight();
        }
        camY = height - Config.getRES_HEIGHT() / 2f;
        camY += item.getTotalHeight() / 2;
    }

    @SuppressLint("SimpleDateFormat")
    public void changeDimensionInfo(TrackInfo track) {
        if (track == null) {
            return;
        }
        float ar = track.getApproachRate();
        float od = track.getOverallDifficulty();
        float cs = track.getCircleSize();
        float hp = track.getHpDrain();
        float bpm_max = track.getBpmMax();
        float bpm_min = track.getBpmMin();
        long length = track.getMusicLength();
        EnumSet<GameMod> mod = ModMenu.getInstance().getMod();

        dimensionInfo.setColor(1, 1, 1);
        beatmapInfo.setColor(1, 1, 1);

        if (mod.contains(GameMod.MOD_EASY)) {
            ar *= 0.5f;
            od *= 0.5f;
            cs -= 1f;
            hp *= 0.5f;
            dimensionInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
        }
        if (mod.contains(GameMod.MOD_HARDROCK)) {
            ar *= 1.4f;
            if (ar > 10) {
                ar = 10;
            }
            od *= 1.4f;
            cs += 1f;
            hp *= 1.4f;
            dimensionInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
        }
        if (ModMenu.getInstance().getChangeSpeed() != 1) {
            float speed = ModMenu.getInstance().getSpeed();
            bpm_max *= speed;
            bpm_min *= speed;
            length /= speed;
            if (speed > 1) {
                beatmapInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
                dimensionInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
            } else if (speed < 1) {
                beatmapInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
                dimensionInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
            }
        } else {
            if (mod.contains(GameMod.MOD_DOUBLETIME)) {
                bpm_max *= 1.5f;
                bpm_min *= 1.5f;
                length *= 2 / 3f;
                beatmapInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
                dimensionInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
            }
            if (mod.contains(GameMod.MOD_NIGHTCORE)) {
                bpm_max *= 1.5f;
                bpm_min *= 1.5f;
                length *= 2 / 3f;
                beatmapInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
                dimensionInfo.setColor(205 / 255f, 85 / 255f, 85 / 255f);
            }
            if (mod.contains(GameMod.MOD_HALFTIME)) {
                bpm_max *= 0.75f;
                bpm_min *= 0.75f;
                length *= 4 / 3f;
                beatmapInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
                dimensionInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
            }
        }
        if (mod.contains(GameMod.MOD_REALLYEASY)) {
            if (mod.contains(GameMod.MOD_EASY)) {
                ar *= 2f;
                ar -= 0.5f;
            }
            ar -= 0.5f;
            if (ModMenu.getInstance().getChangeSpeed() != 1) {
                ar -= ModMenu.getInstance().getSpeed() - 1.0f;
            } else if (mod.contains(GameMod.MOD_DOUBLETIME) || mod.contains(GameMod.MOD_NIGHTCORE)) {
                ar -= 0.5f;
            }
            od *= 0.5f;
            cs -= 1f;
            hp *= 0.5f;
            dimensionInfo.setColor(46 / 255f, 139 / 255f, 87 / 255f);
        }

        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String binfoStr = String.format(StringTable.get(R.string.binfoStr1), sdf.format(length),
                (bpm_min == bpm_max ? GameHelper.Round(bpm_min, 1) : GameHelper.Round(bpm_min, 1) + "-" + GameHelper.Round(bpm_max, 1)),
                track.getMaxCombo());
        if (length > 3600 * 1000) {
            sdf = new SimpleDateFormat("HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+0"));
            binfoStr = String.format(StringTable.get(R.string.binfoStr1), sdf.format(length),
                    (bpm_min == bpm_max ? GameHelper.Round(bpm_min, 1) : GameHelper.Round(bpm_min, 1) + "-" + GameHelper.Round(bpm_max, 1)),
                    track.getMaxCombo());
        }
        beatmapInfo.setText(binfoStr);

        final StringBuilder dimensionStringBuilder = new StringBuilder();
        ar = Math.min(13.f, ar);
        od = Math.min(10.f, od);
        cs = Math.min(15.f, cs);
        hp = Math.min(10.f, hp);
        if (ModMenu.getInstance().getChangeSpeed() != 1) {
            float speed = ModMenu.getInstance().getSpeed();
            ar = GameHelper.Round(GameHelper.ms2ar(GameHelper.ar2ms(ar) / speed), 2);
            od = GameHelper.Round(GameHelper.ms2od(GameHelper.od2ms(od) / speed), 2);
        } else if (mod.contains(GameMod.MOD_DOUBLETIME) || mod.contains(GameMod.MOD_NIGHTCORE)) {
            ar = GameHelper.Round(GameHelper.ms2ar(GameHelper.ar2ms(ar) * 2 / 3), 2);
            od = GameHelper.Round(GameHelper.ms2od(GameHelper.od2ms(od) * 2 / 3), 2);
        } else if (mod.contains(GameMod.MOD_HALFTIME)) {
            ar = GameHelper.Round(GameHelper.ms2ar(GameHelper.ar2ms(ar) * 4 / 3), 2);
            od = GameHelper.Round(GameHelper.ms2od(GameHelper.od2ms(od) * 4 / 3), 2);
        }
        float rawAR = ar;
        float rawOD = od;
        float rawCS = cs;
        float rawHP = hp;

        if (ModMenu.getInstance().isCustomAR()) {
            ar = ModMenu.getInstance().getCustomAR();
        }
        if (ModMenu.getInstance().isCustomOD()) {
            od = ModMenu.getInstance().getCustomOD();
        }
        if (ModMenu.getInstance().isCustomCS()) {
            cs = ModMenu.getInstance().getCustomCS();
        }
        if (ModMenu.getInstance().isCustomHP()) {
            hp = ModMenu.getInstance().getCustomHP();
        }

        if (ar != rawAR || od != rawOD || cs != rawCS || hp != rawHP) {
            dimensionInfo.setColor(256 / 255f, 180 / 255f, 0 / 255f);
        }

        dimensionStringBuilder.append("AR: ").append(GameHelper.Round(ar, 2)).append(" ")
                .append("OD: ").append(GameHelper.Round(od, 2)).append(" ")
                .append("CS: ").append(GameHelper.Round(cs, 2)).append(" ")
                .append("HP: ").append(GameHelper.Round(hp, 2)).append(" ")
                .append("Stars: ").append(GameHelper.Round(track.getDifficulty(), 2));

        dimensionInfo.setText(dimensionStringBuilder.toString());
    }

    public void updateInfo(TrackInfo track) {
        if (track == null) {
            return;
        }

        String tinfoStr = (track.getBeatmap().getArtistUnicode() == null || Config.isForceRomanized() ? track.getBeatmap().getArtist() : track.getBeatmap().getArtistUnicode()) + " - " +
                (track.getBeatmap().getTitleUnicode() == null || Config.isForceRomanized() ? track.getBeatmap().getTitle() : track.getBeatmap().getTitleUnicode()) + " [" + track.getMode() + "]";
        String mapperStr = "Beatmap by " + track.getCreator();
        String binfoStr2 = String.format(StringTable.get(R.string.binfoStr2),
                track.getHitCircleCount(), track.getSliderCount(), track.getSpinnerCount(), track.getBeatmapSetID());
        trackInfo.setText(tinfoStr);
        mapper.setText(mapperStr);
        beatmapInfo2.setText(binfoStr2);
        changeDimensionInfo(track);
        Async.run(() -> {
            BeatmapData beatmapData = new BeatmapParser(track.getFilename()).parse(true);

            if (beatmapData == null) {
                setStarsDisplay(0);
                return;
            }

            beatmapData.populateMetadata(track);
            changeDimensionInfo(track);

            DifficultyCalculationParameters parameters = new DifficultyCalculationParameters();
            var modMenu = ModMenu.getInstance();

            parameters.mods = modMenu.getMod();
            parameters.customSpeedMultiplier = modMenu.getChangeSpeed();

            if (modMenu.isCustomCS()) {
                parameters.customCS = modMenu.getCustomCS();
            }
            if (modMenu.isCustomAR()) {
                parameters.customAR = modMenu.getCustomAR();
            }
            if (modMenu.isCustomOD()) {
                parameters.customOD = modMenu.getCustomOD();
            }

            DifficultyAttributes attributes = BeatmapDifficultyCalculator.calculateDifficulty(
                    beatmapData,
                    parameters
            );

            setStarsDisplay(GameHelper.Round(attributes.starRating, 2));
        });
    }

    public void selectTrack(final TrackInfo track, boolean reloadBG) {

        // Playing corresponding audio for the selected track.
        var selectedAudioTrack = this.selectedTrack != null ? this.selectedTrack : GlobalManager.getInstance().getSelectedTrack();

        if (selectedAudioTrack == null || !Objects.equals(selectedAudioTrack.getAudioFilename(), track.getAudioFilename())) {
            playMusic(track.getAudioFilename(), track.getPreviewTime());
        }

        if (board.isShowOnlineScores()) {
            Async.run(this::setRank);
        }

        if (selectedTrack == track) {
            synchronized (bgLoaded) {
                if (!bgLoaded) {
                    return;
                }
            }

            ResourceManager.getInstance().getSound("menuhit").play();
            if (Multiplayer.isMultiplayer)
            {
                setMultiplayerRoomBeatmap(selectedTrack);
                back(false);
                return;
            }
            stopMusic();

            Replay.oldMod = ModMenu.getInstance().getMod();
            Replay.oldChangeSpeed = ModMenu.getInstance().getChangeSpeed();

            Replay.oldCustomAR = ModMenu.getInstance().getCustomAR();
            Replay.oldCustomOD = ModMenu.getInstance().getCustomOD();
            Replay.oldCustomCS = ModMenu.getInstance().getCustomCS();
            Replay.oldCustomHP = ModMenu.getInstance().getCustomHP();

            Replay.oldFLFollowDelay = ModMenu.getInstance().getFLfollowDelay();

            game.startGame(track, null);
            unload();
            return;
        }
        isSelectComplete = false;
        selectedTrack = track;
        EdExtensionHelper.onSelectTrack(track);
        GlobalManager.getInstance().setSelectedTrack(track);
        updateInfo(track);
        board.init(track);

        final int quality = Config.getBackgroundQuality();
        synchronized (backgroundMutex) {

            if (!reloadBG && (track.getBackground() == null || bgName.equals(track.getBackground()))) {
                isSelectComplete = true;
                return;
            }
            bgName = track.getBackground();
            bg = null;
            bgLoaded = false;
            scene.setBackground(new ColorBackground(0, 0, 0));
            if (quality == 0) {
                Config.setBackgroundQuality(4);
            }
        }

        Async.run(() -> {
            synchronized (backgroundMutex) {
                final TextureRegion tex = Config.isSafeBeatmapBg() || track.getBackground() == null?
                        ResourceManager.getInstance().getTexture("menu-background") :
                        ResourceManager.getInstance().loadBackground(bgName);
                if (tex != null) {
                    float height = tex.getHeight();
                    height *= Config.getRES_WIDTH()
                            / (float) tex.getWidth();
                    bg = new Sprite(0,
                            (Config.getRES_HEIGHT() - height) / 2, Config
                            .getRES_WIDTH(), height, tex);
                    bg.setColor(0, 0, 0);
                }

                SyncTaskManager.getInstance().run(() -> {
                    synchronized (backgroundMutex) {
                        if (bg == null) {
                            final TextureRegion tex1 = ResourceManager
                                    .getInstance().getTexture("menu-background");
                            float height = tex1.getHeight();
                            height *= Config.getRES_WIDTH()
                                    / (float) tex1.getWidth();
                            bg = new Sprite(
                                    0,
                                    (Config.getRES_HEIGHT() - height) / 2,
                                    Config.getRES_WIDTH(), height, tex1);
                            bgName = "";
                        }
                        scene.setBackground(new SpriteBackground(bg));
                        Config.setBackgroundQuality(quality);
                        synchronized (bgLoaded) {
                            bgLoaded = true;
                        }
                    }
                });
            }

            isSelectComplete = true;
        });
    }

    public void stopScroll(final float y) {
        velocityY = 0;
        touchY = y;
        initalY = -1;
    }

    public void updateScore() {
        board.init(selectedTrack);
        if (selectedItem != null) {
            selectedItem.updateMarks();
        }
    }

    public void openScore(final int id, boolean showOnline, final String playerName) {
        if (showOnline) {
            engine.setScene(new LoadingScreen().getScene());
            ToastLogger.showTextId(R.string.online_loadrecord, false);
            new AsyncTask() {
                @Override
                public void run() {
                    try {
                        String scorePack = OnlineManager.getInstance().getScorePack(id);
                        String[] params = scorePack.split("\\s+");
                        if (params.length < 11) return;

                        StatisticV2 stat = new StatisticV2(params);
                        if (stat.isLegacySC()) {
                            stat.processLegacySC(selectedTrack);
                        }

                        stat.setPlayerName(playerName);
                        scoreScene.load(stat, null, null, OnlineManager.getReplayURL(id), null, selectedTrack);
                        engine.setScene(scoreScene.getScene());

                    } catch (OnlineManagerException e) {
                        Debug.e("Cannot load play info: " + e.getMessage(), e);
                        engine.setScene(scene);
                    }

                }
            }.execute();
            return;
        }


        StatisticV2 stat = ScoreLibrary.getInstance().getScore(id);
        if (stat.isLegacySC()) {
            stat.processLegacySC(selectedTrack);
        }

        scoreScene.load(stat, null, null, stat.getReplayName(), null, selectedTrack);
        engine.setScene(scoreScene.getScene());
    }


    public void onScroll(final float where) {
        velocityY = 0;
        camY = where - Config.getRES_HEIGHT() / 2f;
    }

    public void unload() {
    }

    public void back() {
        back(true);
    }

    private void back(boolean resetMultiplayerBeatmap) {
        unbindDataBaseChangedListener();

        if (Multiplayer.isMultiplayer) {
            if (resetMultiplayerBeatmap) {
                resetMultiplayerRoomBeatmap();
            }

            RoomScene.INSTANCE.show();
            return;
        }

        GlobalManager.getInstance().getMainScene().show();
    }

    private void resetMultiplayerRoomBeatmap() {
        if (!Multiplayer.isMultiplayer) {
            return;
        }

        // Locking host from change beatmap before the server responses to beatmapChange
        RoomScene.awaitBeatmapChange = true;

        if (!Multiplayer.isConnected()) {
            return;
        }

        // Now we update the beatmap
        if (Multiplayer.room != null && Multiplayer.room.getPreviousBeatmap() != null) {
            var beatmap = Multiplayer.room.getPreviousBeatmap();

            RoomAPI.changeBeatmap(
                    beatmap.getMd5(),
                    beatmap.getTitle(),
                    beatmap.getArtist(),
                    beatmap.getVersion(),
                    beatmap.getCreator()
            );
        } else {
            RoomAPI.changeBeatmap();
        }
    }

    private void setMultiplayerRoomBeatmap(TrackInfo track) {
        if (!Multiplayer.isMultiplayer) {
            return;
        }

        // Locking host from change beatmap before the server responses to beatmapChange
        RoomScene.awaitBeatmapChange = true;

        if (!Multiplayer.isConnected()) {
            return;
        }

        // Now we update the beatmap
        if (track != null) {
            RoomAPI.changeBeatmap(
                    track.getMD5(),
                    track.getBeatmap().getTitle(),
                    track.getBeatmap().getArtist(),
                    track.getMode(),
                    track.getCreator()
            );
        } else {
            RoomAPI.changeBeatmap();
        }
    }

    public void bindDataBaseChangedListener() {
        OdrDatabase.get().setOnDatabaseChangedListener(this::reloadScoreBroad);
    }

    public void unbindDataBaseChangedListener() {
        OdrDatabase.get().setOnDatabaseChangedListener(null);
    }

    public void setY(final float y) {
        velocityY = 0;
        camY = y;
    }

    public void stopMusic() {
        synchronized (musicMutex) {
            if (GlobalManager.getInstance().getSongService() != null) {
                GlobalManager.getInstance().getSongService().stop();
            }
        }
    }

    public void playMusic(final String filename, final int previewTime) {
        if (!Config.isPlayMusicPreview()) {
            return;
        }

        Async.run(() -> {
            synchronized (musicMutex) {
                if (GlobalManager.getInstance().getSongService() != null) {
                    GlobalManager.getInstance().getSongService().stop();
                }

                try {
                    GlobalManager.getInstance().getSongService().preLoad(filename);
                    GlobalManager.getInstance().getSongService().play();
                    GlobalManager.getInstance().getSongService().setVolume(0);
                    if (previewTime >= 0) {
                        GlobalManager.getInstance().getSongService().seekTo(previewTime);
                    } else {
                        GlobalManager.getInstance().getSongService().seekTo(GlobalManager.getInstance().getSongService().getLength() / 2);
                    }
                } catch (final Exception e) {
                    Debug.e("LoadingMusic: " + e.getMessage(), e);
                }
            }
        });
    }

    public boolean isSelectAllowed() {
        if (!bgLoaded) {
            return false;
        }
        return secondsSinceLastSelect > 0.5f;
    }

    public void showPropertiesMenu(MenuItem item) {
        if (item == null) {
            if (selectedItem == null) {
                return;
            }
            item = selectedItem;
        }
        (new PropsMenuFragment()).show(SongMenu.this, item);
    }

    public void showDeleteScoreMenu(int scoreId) {
        (new ScoreMenuFragment()).show(scoreId);
    }

    public void reloadScoreBroad() {
        board.init(selectedTrack);
    }

    public void select() {
        if (GlobalManager.getInstance().getSelectedTrack() != null) {
            BeatmapInfo beatmapInfo = GlobalManager.getInstance().getSelectedTrack().getBeatmap();

            var i = items.size() - 1;
            while (i >= 0) {
                var item = items.get(i);
                if (item.getBeatmap().equals(beatmapInfo)) {
                    secondsSinceLastSelect = 2;
                    item.select(false, true);
                    break;
                }
                --i;
            }
        }
    }

    public TrackInfo getSelectedTrack() {
        return selectedTrack;
    }

    private void tryReloadMenuItems(SortOrder order) {
        switch (order) {
            case Title:
            case Artist:
            case Creator:
            case Date:
            case Bpm:
                reloadMenuItems(GroupType.MapSet);
                break;
            case Stars:
            case Length:
                reloadMenuItems(GroupType.SingleDiff);
                break;
        }
    }

    private void reloadMenuItems(GroupType type) {
        if (!groupType.equals(type)) {
            groupType = type;
            float oy = 10;
            for (MenuItem item : items) {
                item.removeFromScene();
            }
            items.clear();
            switch (type) {
                case MapSet:
                    for (final BeatmapInfo i : LibraryManager.INSTANCE.getLibrary()) {
                        final MenuItem item = new MenuItem(this, i);
                        items.add(item);
                        item.attachToScene(scene, backLayer);
                        oy += item.getHeight();
                    }
                    break;
                case SingleDiff:
                    for (final BeatmapInfo i : LibraryManager.INSTANCE.getLibrary()) {
                        for (int j = 0; j < i.getCount(); j++) {
                            final MenuItem item = new MenuItem(this, i, j);
                            items.add(item);
                            item.attachToScene(scene, backLayer);
                            oy += item.getHeight();
                        }
                    }
                    break;
            }
            final String lowerFilter = FilterMenu.getInstance().getFilter().toLowerCase();
            final boolean favsOnly = FilterMenu.getInstance().isFavoritesOnly();
            final Set<String> limit = FilterMenu.getInstance().getFavoriteFolder() == null ? null : FavoriteLibrary.get().getMaps(FilterMenu.getInstance().getFavoriteFolder());
            for (final MenuItem item : items) {
                item.applyFilter(lowerFilter, favsOnly, limit);
            }
            System.gc();
        }
    }

    public void setStarsDisplay(float star) {
        String str = dimensionInfo.getText();
        String[] strs = str.split("Stars: ");
        if (strs.length == 2) {
            dimensionInfo.setText(strs[0] + "Stars: " + star);
        }
    }

    private void reSelectItem(String oldTrackFileName) {
        if (!oldTrackFileName.equals("")) {
            if (selectedTrack.getFilename().equals(oldTrackFileName) && items.size() > 1 && selectedItem != null && selectedItem.isVisible()) {
                velocityY = 0;
                float height = 0;
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i) == selectedItem) {
                        break;
                    }
                    height += items.get(i).getInitialHeight();
                }
                camY = height - Config.getRES_HEIGHT() / 2f;
                camY += items.get(0).getTotalHeight() / 2;
                return;
            }
            for (final MenuItem item : items) {
                if (item == null || !item.isVisible()) continue;
                int trackid = item.tryGetCorrespondingTrackId(oldTrackFileName);
                if (trackid >= 0) {
                    item.select(true, true);
                    if (trackid != 0) {
                        item.selectTrack(item.getTrackSpritesById(trackid), false);
                    }
                    break;
                }
            }
        }
    }

    public enum SortOrder {
        Title, Artist, Creator, Date, Bpm, Stars, Length
    }

    public enum GroupType {
        MapSet, SingleDiff
    }
}
