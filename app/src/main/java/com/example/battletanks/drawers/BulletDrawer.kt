package com.example.battletanks.drawers

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.battletanks.activities.CELL_SIZE
import com.example.battletanks.GameCore
import com.example.battletanks.R
import sounds.MainSoundPlayer
import com.example.battletanks.enums.Direction
import com.example.battletanks.enums.Material
import com.example.battletanks.models.Bullet
import com.example.battletanks.models.Coordinate
import com.example.battletanks.models.Element
import com.example.battletanks.models.Tank
import com.example.battletanks.utils.checkViewCanMoveThroughBorder
import com.example.battletanks.utils.getElementByCoordinates
import com.example.battletanks.utils.getTankByCoordinates
import com.example.battletanks.utils.getViewCoordinate
import com.example.battletanks.utils.runOnUiThread
import com.example.battletanks.enums.Direction.DOWN
import com.example.battletanks.enums.Direction.UP
import com.example.battletanks.enums.Direction.LEFT
import com.example.battletanks.enums.Direction.RIGHT


private const val BULLET_WIDTH = 15
private const val BULLET_HEIGHT = 25

class BulletDrawer (
    private val container: FrameLayout,
    private val elements:MutableList<Element>,
    private val enemyDrawer: EnemyDrawer,
    private val soundManager: MainSoundPlayer,
    private val gameCore: GameCore
) {

    init {
        moveAllBullets()
    }

    private val allBullets = mutableListOf<Bullet>()

    fun addNewBulletForTank(tank: Tank) {
        val view = container.findViewById<View>(tank.element.viewId) ?: return
        if (tank.alreadyHasBullet()) return
        allBullets.add(Bullet(createBullet(view, tank.direction), tank.direction, tank))
        soundManager.bulletShot()
    }

    private fun Tank.alreadyHasBullet(): Boolean =
        allBullets.firstOrNull {it.tank == this} != null

    private fun moveAllBullets() {
        Thread(Runnable {
            while(true) {
                if (!gameCore.isPlaying()) {
                    continue
                }
                interactWithBullets()
                Thread.sleep(30)
            }
        }).start()
    }

    private fun interactWithBullets() {
        allBullets.toList().forEach() { bullet ->
            val view = bullet.view
            if (bullet.canBulletGoFurther()) {
                when (bullet.direction) {
                    UP -> (view.layoutParams as FrameLayout.LayoutParams).topMargin -= BULLET_HEIGHT
                    DOWN -> (view.layoutParams as FrameLayout.LayoutParams).topMargin += BULLET_HEIGHT
                    LEFT -> (view.layoutParams as FrameLayout.LayoutParams).leftMargin -= BULLET_HEIGHT
                    RIGHT -> (view.layoutParams as FrameLayout.LayoutParams).leftMargin += BULLET_HEIGHT
                }
                chooseBehaviorInTermsOfDirections(bullet)
                container.runOnUiThread {
                    container.removeView(view)
                    container.addView(view)
                }
            } else {
                stopBullet(bullet)
            }
            bullet.stopIntersectingBullet()
        }
       removeInconsistentBullets()
    }

    private fun removeInconsistentBullets(){
        val removingList = allBullets.filter { !it.canMoveFurther}
        removingList.forEach {
            stopBullet(it)
            container.runOnUiThread {
                container.removeView(it.view)
            }
        }
        allBullets.removeAll(removingList)
    }

    private fun Bullet.stopIntersectingBullet() {
        val bulletCoordinate = this.view.getViewCoordinate()
        for (bulletInList in allBullets) {
            val coordinateList = bulletInList.view.getViewCoordinate()
            if (this == bulletInList) {
                continue
            }
            if (coordinateList == bulletCoordinate) {
                stopBullet(this)
                stopBullet(bulletInList)
                return
            }
        }
    }

    private fun Bullet.canBulletGoFurther() =
        this.view.checkViewCanMoveThroughBorder(this.view.getViewCoordinate())
                && this.canMoveFurther

    private fun chooseBehaviorInTermsOfDirections(bullet: Bullet) {
        when (bullet.direction) {
            DOWN, UP -> {
                compareCollections(getCoordinatesForTopOrBottomDirection(bullet), bullet)
            }
            LEFT, RIGHT -> {
                compareCollections(getCoordinatesForLeftOrRightDirection(bullet), bullet)
            }
        }
    }

    private fun compareCollections(detectedCoordinatesList: List<Coordinate>, bullet: Bullet) {
       for (coordinate in detectedCoordinatesList) {
           var element = getTankByCoordinates(coordinate, enemyDrawer.tanks)
           if (element == null) {
               element = getElementByCoordinates(coordinate, elements)
           }
           if (element == bullet.tank.element) {
               continue
           }
           removeElementsAndStopBullet(element, bullet)
       }
    }

    private fun removeElementsAndStopBullet(element: Element?, bullet: Bullet) {
        if (element != null) {
            if (bullet.tank.element.material == Material.ENEMY_TANK
                && element.material == Material.ENEMY_TANK
            ) {
                stopBullet(bullet)
                return
            }
            if (element.material.bulletCanGoThrough) {
                return
            }
            if (element.material.simpleBulletCanDestroy) {
                stopBullet(bullet)
                removeView(element)
                removeElement(element)
                stopGameIfNecessary(element)
                removeTank(element)
            } else {
                stopBullet(bullet)
            }
        }
    }

    private fun removeElement(element: Element) {
        elements.remove(element)
    }

    private fun stopGameIfNecessary(element: Element) {
        if (element.material == Material.PLAYER_TANK || element.material == Material.EAGLE) {
            gameCore.destroyPlayerOrBase(enemyDrawer.getPlayerScore())
        }
    }

    private fun removeTank(element: Element?) {
        val tanksElements = enemyDrawer.tanks.map {it.element}
        val tankIndex = tanksElements.indexOf(element)
        enemyDrawer.removeTank(tankIndex)
        if (tankIndex < 0) return
        soundManager.bulletBurst()
        enemyDrawer.removeTank(tankIndex)
    }

    private fun stopBullet(bullet: Bullet) {
        bullet.canMoveFurther = false
    }

    private fun removeView(element: Element?) {
        val activity = container.context as Activity
        activity.runOnUiThread {
            if (element != null) {
                container.removeView(activity.findViewById(element.viewId))
            }
        }
    }

    private fun getCoordinatesForTopOrBottomDirection(bullet: Bullet): List<Coordinate> {
        val bulletCoordinate = bullet.view.getViewCoordinate()
        val leftCell = bulletCoordinate.left - bulletCoordinate.left % CELL_SIZE
        val rightCell = leftCell + CELL_SIZE
        val topCoordinate = bulletCoordinate.top - bulletCoordinate.top % CELL_SIZE
        return listOf(
            Coordinate(topCell, leftCoordinate),
            Coordinate(bottomCell, leftCoordinate)
        )
    }

    private fun getCoordinatesForLeftOrRightDirection(bulletCoordinate: Coordinate): List<Coordinate> {
        val topCell = bulletCoordinate.top - bulletCoordinate.top % CELL_SIZE
        val bottomCell = topCell + CELL_SIZE
        val leftCoordinate = bulletCoordinate.left - bulletCoordinate.left % CELL_SIZE
        return listOf(
            Coordinate(topCell, leftCoordinate),
            Coordinate(bottomCell, leftCoordinate)
        )
    }

    private fun createBullet(myTank: View, currentDirection: Direction): ImageView {
        return ImageView(container.context)
            .apply {
                this.setImageResource(R.drawable.bullet)
                this.layoutParams = FrameLayout.LayoutParams(BULLET_WIDTH, BULLET_HEIGHT)
                val bulletCoordinate = getBulletCoordinates(this, myTank, currentDirection)
                (this.layoutParams as FrameLayout.LayoutParams).topMargin = bulletCoordinate.top
                (this.layoutParams as FrameLayout.LayoutParams).leftMargin = bulletCoordinate.left
                this.rotation = currentDirection.rotation
            }
    }

    private fun getBulletCoordinates(
        bullet: ImageView,
        myTank: View,
        currentDirection: Direction
    ): Coordinate {
        val tankLeftTopCoordinate = Coordinate(myTank.top, myTank.left)
        return when (currentDirection) {
            Direction.UP -> Coordinate(
                    top = tankLeftTopCoordinate.top - bullet.layoutParams.height,
                    left = getDistanceToMiddleOfTank(
                        tankLeftTopCoordinate.left,
                        bullet.layoutParams.width
                    )
                )

            Direction.DOWN -> Coordinate(
                    top = tankLeftTopCoordinate.top + myTank.layoutParams.height,
                    left = getDistanceToMiddleOfTank(
                        tankLeftTopCoordinate.left,
                        bullet.layoutParams.width
                    )
                )

            Direction.LEFT -> Coordinate(
                    top = getDistanceToMiddleOfTank(
                        tankLeftTopCoordinate.top,
                        bullet.layoutParams.height
                    ),
                    left = tankLeftTopCoordinate.left - bullet.layoutParams.width
                )

            Direction.RIGHT -> Coordinate(
                    top = getDistanceToMiddleOfTank(
                        tankLeftTopCoordinate.top,
                        bullet.layoutParams.height
                    ),
                    left = tankLeftTopCoordinate.left + myTank.layoutParams.width
                )
        }
    }

    private fun getDistanceToMiddleOfTank(startCoordinate: Int, bulletSize: Int): Int {
        return startCoordinate + (CELL_SIZE - bulletSize/2)
    }
}