package org.firstinspires.ftc.teamcode.Autonomous;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;

import org.firstinspires.ftc.teamcode.Robot.RobotArm;

//@Autonomous(name = "Skystone Stone Fast", group = "ftcPio")
public class SkystoneSideFastRedOld extends Auto {

    public boolean endOnWall = false;

    @Override
    public void runOpMode() {
        startRobot();
        robot.arm.rotationMode = RobotArm.ArmThreadMode.Enabled;

        speed_high = 0.5;
        speed_med = 0.30;
        speed_low = 0.1;

        while (!opModeIsActive()) {

            if (gamepad1.x) {
                break;
            }

            if (gamepad1.a) {
                endOnWall = !endOnWall;
                sleep(500);
            }

            telemetry.addData("End state: ", endOnWall ? "Ending on WALL" : "Ending on BRIDGE");
            telemetry.addData("Press X to continue... ", "Press A to toggle wall state");
            telemetry.update();
        }

        waitForStart();

        //0.035 == lift
        deployGripper(true, 0.030);


//        int cycles = 2;
//
////        for (int stone = 1; stone <= cycles; stone++) {
////            runDeliveryCycle(stone == 1 ? 93 : 30, 1000, 35, stone * 24, 130 + (stone * 30), stone != cycles);
////        }

        runDeliveryCycle(93, 1000, 50, 24, 130 + (24), true);
        runDeliveryCycle(45, 1000, 35, 24, 130 + (48), false);


        robot.driveByDistance(180, 0.75, 22.86);
        if (endOnWall) {
            robot.driveByDistance(-90, 0.8, 70);
        } else {
            robot.driveByDistance(90, 0.8, 50);
        }

//        160
//        193
//        217
//
//        runDeliveryCycle(93, 1000, 35, 24, 160, true);
//
//        runDeliveryCycle(45, 1000, 35, 1 * 24 + 24, 145 + 48, true);
//
//        runDeliveryCycle(45, 1000, 35, 2 * 24 + 24, 145 + (3 * 24), false);

        StopMovement();
        StopRobot();
    }

    //Deploys the gripper, enabling async will have the arm movement happen in the background without pausing the main thread.
    private void deployGripper(boolean async, double armLiftAmount) {

        //Sets the gripper to an idle state
        robot.arm.setGripState(RobotArm.GripState.CLOSED, 1);

        //Extends the arm
        robot.arm.setArmStateWait(0, 0.65);

        sleep(500);

        //Deploys the gripper
        robot.arm.setGripState(RobotArm.GripState.OPEN, 0.5);

        sleep(800);

        if (async) {
            robot.arm.setArmStateAsync(armLiftAmount, 0.3);
        } else {
            robot.arm.setArmStateWait(armLiftAmount, 0.3);
        }
    }

    private void runDeliveryCycle(double fwdDistance, long servoDelayMS, double distanceFromStone, double endingOffset, double bridgeDistance, boolean moveBackToBridge) {

        //Drives forward so the arm is making contact with the stone
        driveToSkystone(fwdDistance);

        //Closes the gripper on the stone
        robot.arm.setGripState(RobotArm.GripState.CLOSED, 0.5);

        //Wait to ensure the gripper is closed
        sleep(servoDelayMS);

        //Drive backwards, breaking sticktion and allowing the gripper to close completely
        robot.driveByDistance(180, 0.5, distanceFromStone, 2);

        //Rotates to face the foundation
        rotateAccurate(-90);

        //Drives to foundation at a high speed
        robot.driveByDistance(0, 1, bridgeDistance, 2.1);

        //Releases the stone
        robot.arm.setGripState(RobotArm.GripState.OPEN, 0.5);

        //Waits to ensure the stone is completely detached
        sleep(servoDelayMS);

        //Resets rotation after speedyness
        rotateFast(-90);

        if (moveBackToBridge) {
            //Wait to make sure the stone is dropped
            sleep(servoDelayMS / 2);

            //Rolls back to the skystone side quickly
            robot.driveByDistance(180, 1, bridgeDistance, 2.7);

            //Resets rotation
            rotateFast(-90);

            //Rolls back again so the bot is aligned with the next stone
            robot.driveByDistance(180, 0.35, endingOffset);

            //Rotates to face the next stone
            rotateFast(0);
        }
    }

    public void driveToSkystone(double distanceFoward) {
        robot.driveByDistance(0, 0.35, distanceFoward, 2.6);
    }

    public void rotateFast(double angle) {
        robot.rotatePID(angle, 1, 6);
//        robot.rotateSimple(angle, 2, 2, 0.5); //This one is a fail safe that will mostly work.
    }


    public void rotateAccurate(double angle) {
        robot.rotatePID(angle, 1, 6, 0.6);
        //robot.rotateSimple(angle, 1, 0.5, 0.25); //This one is a fail safe that will mostly work.
    }
}
