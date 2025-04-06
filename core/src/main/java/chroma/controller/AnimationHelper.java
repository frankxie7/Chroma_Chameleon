package chroma.controller;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class AnimationHelper {

    public static Animation<TextureRegion> createAnimation(Texture sheet, int frameCount, float frameDuration) {
        int totalWidth = sheet.getWidth();
        int totalHeight = sheet.getHeight();
        int frameWidth = totalWidth / frameCount;
        int frameHeight = totalHeight;

        // 按单行切割
        TextureRegion[][] tmp = TextureRegion.split(sheet, frameWidth, frameHeight);
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = tmp[0][i];
        }

        Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }
}
