/**
*   @Author: Junko
*   @Email: imaizumikagerouzi@foxmail.com
*   @Date: 4/12/2021 下午3:57
*/
package com.madmath.core.entity.creature;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.AtomicQueue;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.madmath.core.animation.AnimationManager;
import com.madmath.core.animation.CustomAnimation;
import com.madmath.core.control.PlayerInputProcessor;
import com.madmath.core.entity.Act.*;
import com.madmath.core.inventory.Item;
import com.madmath.core.inventory.equipment.Equipment;
import com.madmath.core.map.TrapTile;
import com.madmath.core.resource.ResourceManager;
import com.madmath.core.screen.AbstractScreen;
import com.madmath.core.screen.GameScreen;
import com.madmath.core.util.Utils;
import com.madmath.core.util.myPair;

import java.util.ArrayDeque;
import java.util.Queue;

public class Player extends Creature{

    public Array<Equipment> weapon;
    public Equipment activeWeapon=null;

    private boolean attackable = true;
    private boolean movable = true;

    public PlayerInputProcessor inputProcessor;

    private final Vector2 subjectiveDirection = new Vector2();

    public float weaponAngle;

    public int score;

    private Image pickArrow;

    private Item pickItem;

    protected boolean sprint = false;

    static public String alias = "knight_f";

    Queue<Act> acts = new ArrayDeque<>(30);

    public boolean hasActs(){
        return acts.peek()!=null;
    }

    static public AnimationManager initPlayerAnim(ResourceManager manager){
        return new AnimationManager(new CustomAnimation(0.34f,new Array<>(manager.knight_f_idle_anim16x28[0])),new CustomAnimation(0.17f,new Array<>(manager.knight_f_run_anim16x28[0])),new CustomAnimation(0.09f,new Array<>(manager.knight_f_run_anim16x28[0])));
    }

    public Player(Integer id, AnimationManager animationManager, GameScreen gameScreen, Vector2 position) {
        super(id, animationManager, gameScreen, position);
    }

    public Player(Integer id, AnimationManager animationManager, GameScreen gameScreen) {
        super(id, animationManager, gameScreen);
    }

    @Override
    public void act(float delta) {
        if(this!=gameScreen.player){
            acts.clear();
        }
        super.act(delta);
        if(sprint) setPlayMode(AnimationManager.PlayMode.Sprint);
        if(activeWeapon!=null&& !activeWeapon.isSwinging()){
            activeWeapon.setRotation(weaponAngle);
        }
        affectedByTile();
        if(movable) currentDirection.set(subjectiveDirection);
        pickArrow.setVisible(pickItem!=null);
        if(pickItem!=null){
            pickArrow.setPosition(pickItem.getX()+pickItem.getHeight()*0.135f,pickItem.getY()+pickItem.getHeight()*0.7f);
        }
        sfxUpdate();
    }

    public void sfxUpdate(){
        if(getState() == State.Move && gameScreen.getState()== AbstractScreen.State.RUNING){
            if(sprint){
                fastSound.resume(fast);
                slowSound.pause(slow);
            }else {
                slowSound.resume(slow);
                fastSound.pause(fast);
            }
        }else {
            fastSound.pause(fast);
            slowSound.pause(slow);
        }
    }

    @Override
    public int getHurt(int damage, Vector2 sufferFrom, float knockbackFactor) {
        if(!attackable) return 0;
        attackable = false;
        gameScreen.getManager().dscream.play();
        knockbackFactor *= (1-toughness);
        sufferFrom.sub(getPosition());
        sufferFrom.x = sufferFrom.x>0?-knockbackFactor:knockbackFactor;
        sufferFrom.y = sufferFrom.y>0?-knockbackFactor:knockbackFactor;
        currentDirection.set(sufferFrom);
        movable = false;
        sufferFrom.scl(-0.08f,-0.08f);
        addAction(Actions.sequence(Actions.color(hurtColor.cpy()),Actions.addAction(Actions.color(color.cpy(),0.27f)),
                Actions.addAction(Actions.sequence(Actions.run(()->{
                    attackable = false;
                }),Actions.repeat(3,Actions.sequence(Actions.alpha(0.5f,0.3f),Actions.alpha(1f,0.3f))),
                Actions.run(()->{
                    attackable = true;
                }))),
                Actions.repeat(5,Actions.sequence(Actions.run(()->{
                    addAcceleration(sufferFrom);
                }),Actions.delay(0.05f))),
                Actions.run(()->{
                    setAcceleration(new Vector2(0,0));
                }),Actions.delay(0.1f),
                Actions.run(()->{
                    movable = true;
                })));
        hp -= damage;
        return damage;
    }

    public void swingWeapon(){
        if(activeWeapon!=null&&isAlive()){
            activeWeapon.use();
            acts.add(new Swing());
        }
    }

    public int getHurt(Monster monster) {
        return getHurt(monster.getDamage(),monster.getPosition().cpy(),monster.getKnockback());
    }

    public void addWeapon(Equipment equipment){
        if(weapon.size>=3){
            throwWeapon(activeWeapon);
        }
        weapon.add(equipment);
        equipment.equippedBy(this);
        setWeapon(weapon.indexOf(equipment,true));
        if(gameScreen.map!=null)    gameScreen.map.livingItem.removeValue(equipment,true);
    }

    public void throwWeapon(Equipment equipment){
        if(weapon.size>0){
            int index = weapon.indexOf(equipment,true);
            weapon.removeValue(equipment,true);
            equipment.beThrown();
            if(equipment==activeWeapon){
                activeWeapon = null;
                if(weapon.size>0){
                    setWeapon(Math.min(index,weapon.size-1));
                }
            }
        }
    }

    public void switchWeapon(int offset){
        if(weapon.size<2 || activeWeapon.isSwinging())return;
        setWeapon(((weapon.indexOf(activeWeapon,true)+offset)+weapon.size)%weapon.size);
    }

    public void setWeapon(int index){
        if(weapon.size>index){
            if(activeWeapon!=null)  activeWeapon.setVisible(false);
            activeWeapon = weapon.get(index);
            activeWeapon.setVisible(true);
            acts.add(new Switch());
        }
    }

    public void affectedByTile(){
        Array<myPair> tiledMapTileVector2Pair = getTileOnFoot(getPosition());
        tiledMapTileVector2Pair.forEach(pair ->{
            if(pair.A instanceof TrapTile && ((TrapTile) pair.A).isActive()){
                getHurt((TrapTile) pair.A,pair.B);
                return;
            }
            if(Utils.entryG.contains(pair.A.getId())){
                gameScreen.nextMap();
            }
        });
    }

    public void setSprint(boolean sprint){
        this.sprint = sprint;
    }

    @Override
    public boolean move(float v) {
        boolean temp = super.move(sprint?2*v:v);
        Vector2 cPoisition = new Vector2();
        Item item = null;
        float distance = 0;
        for (int i = 0; i<gameScreen.map.livingItem.size;i++){
            if(gameScreen.map.livingItem.get(i).canPickUp(box.getCenter(cPoisition))){
                float itDistance = Vector2.dst2(gameScreen.map.livingItem.get(i).getItemPosition().x,gameScreen.map.livingItem.get(i).getItemPosition().y,getX(),getY());
                if(item == null || itDistance < distance){
                    distance = itDistance;
                        item = gameScreen.map.livingItem.get(i);
                }
            }
        }
        pickItem = item;
        return temp;
    }

    @Override
    public void Die() {
        super.Die();
        clearActions();
        addAction(Actions.sequence(Actions.color(color.cpy()),Actions.alpha(0.3f)));
        attackable = false;
        movable = true;
        slowSound.pause(slow);
        fastSound.pause(fast);
        gameScreen.map.livingEntity.removeValue(this,true);

    }

    @Override
    public void Resurrect(int Percent) {
        super.Resurrect(Percent);
        addAction(Actions.alpha(1f));
        attackable = true;
        if(!gameScreen.map.livingEntity.contains(this,true))gameScreen.map.livingEntity.add(this);
        acts.add(new Resurrect());
    }

    public void pickUp(){
        if(pickItem == null || !isAlive())return;
        if(pickItem instanceof Equipment){
            addWeapon((Equipment) pickItem);
            acts.add(new Pick());
        }
        pickItem = null;
    }

    public void addSubjectiveDirection(Vector2 Direction){
        subjectiveDirection.x = Math.min(Math.max(subjectiveDirection.x + Direction.x,-1),1);
        subjectiveDirection.y = Math.min(Math.max(subjectiveDirection.y + Direction.y,-1f),1f);
    }

    long slow;
    long fast;
    Sound slowSound;
    Sound fastSound;

    @Override
    public void initSelf() {
        super.initSelf();
        score = 0;
        weapon = new Array<>(3);
        speed = 48f;
        maxHp = 100;
        hp = 100;
        box = new Rectangle(0,0,12,7);
        boxOffset = new Vector2(2,0);
        pickArrow = new Image(new TextureRegionDrawable(gameScreen.getManager().arrow21x21));
        slowSound = gameScreen.getManager().moveSlow;
        fastSound = gameScreen.getManager().moveFast;
        slow = slowSound.play();
        fast = fastSound.play();
        slowSound.setLooping(slow,true);
        fastSound.setLooping(fast,true);
        slowSound.pause(slow);
        fastSound.pause(fast);
        //lostSpeed = 1.8f;
    }

    @Override
    protected void setStage(Stage stage) {
        if(stage!=null)stage.addActor(pickArrow);
        super.setStage(stage);
    }

    public void freshSelf(){
        setAcceleration(new Vector2(0,0));
        score = 0;
    }

    public Output writeAct(Output output){
        output.writeInt(hp);
        output.writeFloat(getPosition().x);
        output.writeFloat(getPosition().y);
        output.writeFloat(subjectiveDirection.x);
        output.writeFloat(subjectiveDirection.y);
        output.writeBoolean(sprint);
        output.writeFloat(weaponAngle);
        output.writeBoolean(anim_dirt);
        Act act = acts.poll();
        while(act!=null){
            output.writeInt(ActFactory.actFactory.getActId(act));
            act.write(output,this);
            act = acts.poll();
        }
        output.writeInt(ActFactory.actFactory.getActId(new End()));
        return output;
    }

    public Output writeAct(byte[] buffers){
        return writeAct(new Output(buffers));
    }

    public Input readAct(Input input){
        hp = input.readInt();
        float px = input.readFloat();
        float py = input.readFloat();
        setPosition(new Vector2(px,py));
        subjectiveDirection.x = input.readFloat();
        subjectiveDirection.y = input.readFloat();
        sprint = input.readBoolean();
        weaponAngle = input.readFloat();
        anim_dirt = input.readBoolean();
        Act act = ActFactory.actFactory.getAct(input.readInt());
        while(!(act instanceof End)){
            act.read(input,this);
            act = ActFactory.actFactory.getAct(input.readInt());
        }
        return input;
    }

    public Input readAct(byte[] buffers){
        return readAct(new Input(buffers));
    }

    public void dispose(){
    }

}

