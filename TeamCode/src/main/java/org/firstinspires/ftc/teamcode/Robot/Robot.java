package org.firstinspires.ftc.teamcode.Robot;

import android.renderscript.Double2;
import android.renderscript.Double4;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoImplEx;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.Helpers.PID;
import org.firstinspires.ftc.teamcode.Helpers.Vector2;
import org.firstinspires.ftc.teamcode.Helpers.bDataManager;
import org.firstinspires.ftc.teamcode.Helpers.bMath;
import org.firstinspires.ftc.teamcode.Helpers.bTelemetry;
import org.firstinspires.ftc.teamcode.Hardware.bIMU;
import org.firstinspires.ftc.teamcode.Hardware.Potentiometer;
import org.firstinspires.ftc.teamcode.Robot.Input.RobotInputThread;

import java.sql.Time;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

//TODO: clean up the canmove system
public class Robot extends Thread {

    //Static instance. Only have one robot at a time and access it from here (THERE CAN BE ONLY ONE)
    public static Robot instance;

    //The linear slide arm, controls length and angle
    public RobotArm arm;

    //WIP WIP WIP, should let us have really responsive sensor data (right now IMU and distance)
    public RobotInputThread experimentalInput = new RobotInputThread();

    //The wall tracker, lets you track along a wall using a sensor group and other data
    public RobotWallTrack wallTrack = new RobotWallTrack();

    public Potentiometer armPotentiometer = null;

    public Servo capstoneServo;
    public Servo foundationServo0;
    public Servo foundationServo1;

    //The current IMU rotation, assigned by a thread
    static double rotation;

    //The IMU reader, takes the average of 2 IMU's to give a fancy (and likely less preferment) reading!
    public bIMU imu = new bIMU();

    //The wheel drive manager, makes sure all wheels are moving at the same speed at all times
    public RobotDriveManager driveManager;

    //Delta time, used in the thread for timing
    public ElapsedTime threadDeltaTime = new ElapsedTime();

    //The data manager serves to store data locally on the phone, used in calibration and PID tuning.
    public bDataManager dataManger = new bDataManager();

    double desiredArmRotationPower;

    public LinearOpMode Op;

    private AtomicBoolean threadRunning = new AtomicBoolean();

    private AtomicLong threadLastRunTime = new AtomicLong(0);

    private PID rotationPID = new PID();

    public void init(LinearOpMode opmode, boolean useWalltrack) {
        //start the printer service
        bTelemetry.start(opmode);

        //Fail safe to make sure there is only one Robot.java running.
//        if (instance != null) {
//            bTelemetry.print("FATAL ERROR: THERE CAN ONLY BE ONE INSTANCE OF ROBOT.JAVA");
//            return;
//        }


        //Set up the instance
        instance = this;
        bTelemetry.print("Robot instance assigned.");

        //Set the opmode
        Op = opmode;

        getHardware(opmode, useWalltrack);

        capstoneServo.setPosition(0.733);

        //Starts the 'run' thread
        start();
        bTelemetry.print("Robot thread initialized.");

        bTelemetry.print("Robot start up successful. Preparing to read wheel calibration data...");

        //Starts the dataManager to read calibration data
        dataManger.Start();

        bTelemetry.print("bDataManager started.");


        //Assign and display calibration data for debugging purposes
        driveManager.frontLeft.powerCoefficent = dataManger.readData("wheel_front_left_powerCo", -1);
        bTelemetry.print("      Front Left  : " + driveManager.frontLeft.powerCoefficent);
        driveManager.frontRight.powerCoefficent = dataManger.readData("wheel_front_right_powerCo", -1);
        bTelemetry.print("      Front Right : " + driveManager.frontRight.powerCoefficent);
        driveManager.backLeft.powerCoefficent = dataManger.readData("wheel_back_left_powerCo", -1);
        bTelemetry.print("      Back Left   : " + driveManager.backLeft.powerCoefficent);
        driveManager.backRight.powerCoefficent = dataManger.readData("wheel_back_right_powerCo", -1);
        bTelemetry.print("      Back Right  : " + driveManager.backRight.powerCoefficent);

        //armPotentiometer.regSlope = dataManger.readData("pot_reg_slope", bMath.toRadians(133));
        //armPotentiometer.regIntercept = dataManger.readData("pot_reg_intercept", bMath.toRadians(-4.62));


        bTelemetry.print("      Arm Regression Slope    : " + armPotentiometer.regSlope);
        bTelemetry.print("      Arm Regression Int      : " + armPotentiometer.regIntercept);

        //Adds the motors and distance sensors to the expInput manager to allow for faster reads
        //DISABLED BUT WORKS
//        bTelemetry.print("Initializing Experimental Input...");
//        for (bMotor motor : driveManager.driveMotors) {
//            experimentalInput.AddMotor(motor);
//        }
//
//        for (DistanceSensor sensor : wallTrack.sensors) {
//            experimentalInput.AddSensor(sensor);
//        }

        arm.setGripState(RobotArm.GripState.IDLE, 0.8);
        setFoundationGripperState(0);

        bTelemetry.print("Wheel boot successful. Ready to operate!");
    }

    public void getHardware(LinearOpMode opmode, boolean useWallTracking) {

        //Sets up the drive train hardware
        bTelemetry.print("Configuring drive train...");
        driveManager = new RobotDriveManager(opmode, RobotConfiguration.wheel_frontLeft, RobotConfiguration.wheel_frontRight, RobotConfiguration.wheel_backLeft, RobotConfiguration.wheel_backRight);

        //Invert the left side wheels
        driveManager.frontLeft.setDirection(DcMotor.Direction.REVERSE);
        driveManager.backLeft.setDirection(DcMotor.Direction.REVERSE);

        //Reset drive train encoders
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);


        //Sets up the arms hardware
        bTelemetry.print("Configuring arm motors...");
        armPotentiometer = new Potentiometer(opmode, RobotConfiguration.armPotentiometer);
        arm = new RobotArm(opmode, RobotConfiguration.arm_rotationMotor, RobotConfiguration.arm_lengthMotor, RobotConfiguration.arm_gripServo, RobotConfiguration.arm_gripRotationServo, new Double2(0, 1), new Double2(0, 1));

        capstoneServo = opmode.hardwareMap.get(Servo.class, RobotConfiguration.capstoneServo);

        bTelemetry.print("Configuring IMU...");
        imu.Start(opmode);

        if (useWallTracking) {
            bTelemetry.print("Configuring wall tracking...");
            wallTrack.Start(opmode);
        }
        foundationServo0 = opmode.hardwareMap.get(Servo.class, RobotConfiguration.foundationGrip0);
        foundationServo1 = opmode.hardwareMap.get(Servo.class, RobotConfiguration.foundationGrip1);

        setFoundationGripperState(1);
        arm.setGripState(RobotArm.GripState.IDLE, 1);


        while (imu.initStatus.get() < 2) {

        }

        bTelemetry.print("Completed IMU start up.");

        while (useWallTracking && !wallTrack.startUpComplete.get()) {

        }

        bTelemetry.print("Completed walltrack start up.");

        bTelemetry.print("Hardware configuration complete.");
    }


    //A fancy version of init used for calibrating the robot, not to be used in any offical match as calibration will take anywhere from 10 to 30 seconds
    public void initCalibration(HardwareMap hardwareMap, LinearOpMode opmode) {

        //start the printer
        bTelemetry.start(opmode);

        //Set up the instance (safety checks might be a good idea at some point)
        instance = this;
        bTelemetry.print("Robot instance assigned.");

        //Set the opmode
        Op = opmode;

        getHardware(opmode, false);
        setFoundationGripperState(0);

//        //Find the motors
//        driveManager = new RobotDriveManager(opmode, RobotConfiguration.wheel_frontLeft, RobotConfiguration.wheel_frontRight, RobotConfiguration.wheel_backLeft, RobotConfiguration.wheel_backRight);
//
//        bTelemetry.print("Robot wheels assigned.");
//        bTelemetry.print("Robot motors configured in the DriveManager.");
//
//        //Left wheels are reversed so power 1,1,1,1 moves us forward
//        driveManager.frontLeft.setDirection(DcMotor.Direction.REVERSE);
//        driveManager.backLeft.setDirection(DcMotor.Direction.REVERSE);
//
//        //Define the arm values for motors and servos (also includes ranges)
//        arm = new RobotArm(opmode, RobotConfiguration.arm_rotationMotor, RobotConfiguration.arm_lengthMotor, RobotConfiguration.arm_gripServo, RobotConfiguration.arm_gripRotationServo, new Double2(0, 1), new Double2(0, 1));
//
//        //start the thread that is responsible for fighting gravity and keeping arm position level.
////        arm.start();
//
//        //Init the motors for use.
//        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
//        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
//        bTelemetry.print("Wheel encoders initialized.");
//
//
//        //Set up the IMU(s)
//        imu.start(opmode);
//        bTelemetry.print("IMU's initialized.");
//
//        //Set up the wall tracker, this uses ALL the lasers so make sure they all work before running this
//        wallTrack.start(opmode);
//        bTelemetry.print("Walltracker initialized.");
//
        //Starts the 'run' thread
        start();
        bTelemetry.print("Robot thread initialized.");

        bTelemetry.print("Robot start up successful. Preparing for initial wheel calibration!");

        dataManger.Start();

        bTelemetry.print("bDataManager started.");

        bTelemetry.print("Robot start up successful. Running initial wheel calibration...");

        setFoundationGripperState(0);

        driveManager.PerformInitialCalibration();

        bTelemetry.print("Wheel boot successful. Writing results...");

        dataManger.writeData("wheel_front_left_powerCo", driveManager.frontLeft.powerCoefficent);
        dataManger.writeData("wheel_front_right_powerCo", driveManager.frontRight.powerCoefficent);
        dataManger.writeData("wheel_back_left_powerCo", driveManager.backLeft.powerCoefficent);
        dataManger.writeData("wheel_back_right_powerCo", driveManager.backRight.powerCoefficent);

        bTelemetry.print("Wheel write successful.");

        bTelemetry.print("Calibration complete, pleasure doing business with you.");


    }

    double threadArmTime = 0;

    double lastTargetPosition;

    //Enabled run method, right now this is just for IMU stuff, at some point we might put some avoidance stuff in here (background wall tracking?)
    public void run() {
        threadRunning.set(true);

        while (threadRunning.get()) {

            //Set the sync value
//            threadLastRunTime.set(:CURRENTTIME:);
            //Update our 'rotation' value
            updateBackgroundRotation();

//            threadTimer += threadDeltaTime.seconds();
//            op.telemetry.update();

            //Make sure that the robot stops once we request a stop
            if (Op.isStopRequested()) {
                setPowerDouble4(0, 0, 0, 0, 0);
                threadRunning.set(false);
            }

            if (arm.extensionMode == RobotArm.ArmThreadMode.Enabled) {
                arm.length.setPower(arm.targetLengthSpeed);
                arm.length.setTargetPosition((int) arm.targetLength);
            }

            if (arm.rotationMode == RobotArm.ArmThreadMode.Enabled) {

                if (arm.targetRotation != lastTargetPosition) {
                    lastTargetPosition = arm.targetRotation;
//                    bTelemetry.print("Arm State Changed");
                    threadArmTime = 0;
                }

//                bTelemetry.print("Arm Time", threadArmTime);
//                bTelemetry.print("Target Arm Position", desiredArmRotationPower);


                desiredArmRotationPower = (arm.targetRotation - arm.rotation.getCurrentPosition()) / RobotConfiguration.arm_rotationMax * 5;

//                arm.rotation.setPower(Math.copySign(0.3, desiredArmRotationPower));

                if (Math.abs(desiredArmRotationPower / 5) > 0.0005) {

                    arm.rotation.setPower(Math.copySign(bMath.Clamp(Math.abs(desiredArmRotationPower), 0.15, 1), desiredArmRotationPower));
                } else {
                    arm.rotation.setPower(0);
                }
//                arm.rotation.setPower(bMath.Clamp(desiredArmRotationPower, Math.copySign(0.025, desiredArmRotationPower), Math.copySign(1, desiredArmRotationPower)));
//                    if (threadArmTime < 1) {
////                        arm.rotation.setPower(1);
//                        arm.rotation.setPower(Math.copySign(1, desiredArmRotationPower));
//                    } else {
//                        arm.rotation.setPower(bMath.Clamp(desiredArmRotationPower, Math.copySign(0.025, desiredArmRotationPower), Math.copySign(1, desiredArmRotationPower)));
//                    }
//                }
            }
        }


    }

    AtomicBoolean rotationRecent = new AtomicBoolean(false);

    public void updateBackgroundRotation() {
        //Updates the current rotation
        rotation = imu.getRotation(AngleUnit.DEGREES);
        rotationRecent.set(true);
    }


    public void shutdown() {

        experimentalInput.Stop();
        threadRunning.set(false);
        setPowerDouble4(0, 0, 0, 0, 0);
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
    }


    //<editor-fold desc="Movement">

    /**
     * Uses
     *
     * @param headingAngle  The angle  that we want to move along, try to keep its magnitude under 180
     * @param movementSpeed How fast we want to move to move along 'headingAngle'. 1 is very fast, 0 is anti-fast (brakes).
     */

    public void moveSimple(double headingAngle, double movementSpeed) {
        Double4 v = bMath.getMecMovementSimple(headingAngle);
        setPowerDouble4(v, movementSpeed);
    }

    /**
     * Uses
     *
     * @param headingVector The vector that we want to move along
     * @param movementSpeed How fast we want to move to move along 'movementAngle'. 1 is very fast, 0 is anti-fast (brakes).
     */

    public void moveSimple(Double2 headingVector, double movementSpeed) {
        Double4 v = bMath.getMecMovementSimple(headingVector);
        setPowerDouble4(v, movementSpeed);
    }

    /**
     * Uses
     *
     * @param headingAngle  The angle  that we want to move along, try to keep its magnitude under 180
     * @param movementSpeed How fast we want to move to move along 'headingAngle'. 1 is very fast, 0 is anti-fast (brakes).
     */

    public void moveSimple(double headingAngle, double movementSpeed, double rotationPower) {
        Double4 v = bMath.getMecMovementSimple(headingAngle, rotationPower);
        setPowerDouble4(v, movementSpeed);
    }

    public void moveSimple(Double2 headingVector, double movementSpeed, double rotationSpeed) {
        Double4 v = bMath.getMecMovementSimple(headingVector, rotationSpeed);
        setPowerDouble4(v, movementSpeed);
    }


    public void moveComplex(double headingAngle, double movementSpeed, double rotationSpeed, double offsetAngle) {
        Double4 v = bMath.getMecMovement(headingAngle, rotationSpeed, offsetAngle);
        setPowerDouble4(v, movementSpeed);
    }


    public void moveComplex(Double2 headingVector, double movementSpeed, double rotationSpeed, double offsetAngle) {
        Double4 v = bMath.getMecMovement(headingVector, rotationSpeed, offsetAngle);
        setPowerDouble4(v, movementSpeed);
    }

    public void rotateSimple(double rotationSpeed) {
        Double4 v = bMath.getRotationSimple(rotationSpeed);
        setPowerDouble4(v, 1);
    }


    @Deprecated
    public void rotatePID(double targetAngle, double rotationSpeed, double maxTime) {

        //P of 3 and 0 for other gains seems to work really well
//        rotationPID.start(3, 0, 0.1);

        rotationPID.start(7.10647, 0, 0.8507085);
//        rotationPID.start(7.10647, 0, 0.754351);
//        rotationPID.start(3.02, 0, 0.085);
//        rotationPID.start(4.01, 0.003, 0.0876);

//        rotationPID.start(1, 0.075, 0.022);

//        rotationPID.start(3, 0.21, 0.69);
//        rotationPID.start(0.5, 0.075, 0.015);
//        rotationPID.start(1, 0.25, 0.035);
//        rotationPID.start(0.025, 0.005, 0);

        double rotationPower;
        double timer = 0;

        ElapsedTime deltaTime = new ElapsedTime();
        double currentRotation = 0;
        while (Op.opModeIsActive()) {

            //Thread safty check to prevent race conditions and mismatched input
            //Waits for new input
            while (!rotationRecent.get()) {

            }
            //Marks input
            rotationRecent.set(false);

            currentRotation = getRotation();

            rotationPower = rotationPID.loop(bMath.DeltaDegree(currentRotation, targetAngle), 0);

            rotationPower = (rotationPower / (360)) * rotationSpeed;
            rotationPower += (0.03 * (rotationPower > 0 ? 1 : -1));

            Op.telemetry.addData("Error ", rotationPID.error);
            Op.telemetry.addData("Last Error  ", rotationPID.lastError);
            Op.telemetry.addData("Derivative ", rotationPID.derivative);
            Op.telemetry.addData("Integral ", rotationPID.integral);
            Op.telemetry.addData("TD ", rotationPID.deltaTime.seconds());
            Op.telemetry.addData("Rotation ", rotation);
            Op.telemetry.addData("rotationPower ", rotationPower);
            Op.telemetry.addData("rotationSpeed ", rotationSpeed);
            Op.telemetry.addData("time ", timer);
            Op.telemetry.update();

            rotateSimple(rotationPower);

            //Exit the PID loop if the bots not rotating or is withing 1.25 degrees of the target angle or if we hit the maxtime
//            Math.abs(rotationPower) < 0.1 ||

//            if (bMath.DeltaDegree(rotation, targetAngle) < 1.25 || timer >= maxTime) {
            if (timer >= maxTime || Math.abs(rotationPID.error) < 0.75) {
                break;
            }

            timer += deltaTime.seconds();

            deltaTime.reset();
        }

        setPowerDouble4(0, 0, 0, 0, 0);

    }


    public void rotatePID(double targetAngle, double rotationSpeed, double maxTime,
                          double overrideExitThreshold) {
        //P of 3 and 0 for other gains seems to work really well
//        rotationPID.start(3, 0, 0.1);

        rotationPID.start(7.10647, 0, 0.8507085);
//        rotationPID.start(7.10647, 0, 0.754351);
//        rotationPID.start(3.02, 0, 0.085);
//        rotationPID.start(4.01, 0.003, 0.0876);

//        rotationPID.start(1, 0.075, 0.022);

//        rotationPID.start(3, 0.21, 0.69);
//        rotationPID.start(0.5, 0.075, 0.015);
//        rotationPID.start(1, 0.25, 0.035);
//        rotationPID.start(0.025, 0.005, 0);

        double rotationPower;
        double timer = 0;

        ElapsedTime deltaTime = new ElapsedTime();

        while (Op.opModeIsActive()) {

            //wait for this to be true
//            while (!rotationRecent.get()) {
//            }
//
//            //Marks input
//            rotationRecent.set(false);

//            rotationPower = rotationPID.loop(bMath.DeltaDegree(rotation, targetAngle), 0);
            rotationPower = rotationPID.loop(bMath.DeltaDegree(imu.imu_0.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle, targetAngle), 0);

            rotationPower = (rotationPower / (360)) * rotationSpeed;
            rotationPower += (0.03 * (rotationPower > 0 ? 1 : -1));

            Op.telemetry.addData("Error ", rotationPID.error);
            Op.telemetry.addData("Last Error  ", rotationPID.lastError);
            Op.telemetry.addData("Derivative ", rotationPID.derivative);
            Op.telemetry.addData("Integral ", rotationPID.integral);
            Op.telemetry.addData("TD ", rotationPID.deltaTime.seconds());
            Op.telemetry.addData("Rotation ", rotation);
            Op.telemetry.addData("rotationPower ", rotationPower);
            Op.telemetry.addData("rotationSpeed ", rotationSpeed);
            Op.telemetry.addData("time ", timer);
            Op.telemetry.update();

            rotateSimple(rotationPower);

            //Exit the PID loop if the bots not rotating or is withing 1.25 degrees of the target angle or if we hit the maxtime
//            Math.abs(rotationPower) < 0.1 ||

//            if (bMath.DeltaDegree(rotation, targetAngle) < 1.25 || timer >= maxTime) {
            if (timer >= maxTime || Math.abs(rotationPID.error) < overrideExitThreshold) {
                break;
            }

            timer += deltaTime.seconds();

            deltaTime.reset();
        }

        setPowerDouble4(0, 0, 0, 0, 0);

    }

    public void rotatePID(double targetAngle, double rotationSpeed, double maxTime, double p,
                          double i, double d) {

        rotationPID.start(p, i, d);

        double rotationPower;
        double timer = 0;

        ElapsedTime deltaTime = new ElapsedTime();

        while (Op.opModeIsActive()) {
            while (!rotationRecent.get()) {

            }
            //Marks input
            rotationRecent.set(false);

            rotationPower = rotationPID.loop(bMath.DeltaDegree(imu.imu_0.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES).firstAngle, targetAngle), 0);
            rotationPower = (rotationPower / (360)) * rotationSpeed;
            rotationPower += (0.03 * (rotationPower > 0 ? 1 : -1));

            Op.telemetry.addData("Error ", rotationPID.error);
            Op.telemetry.addData("Last Error  ", rotationPID.lastError);
            Op.telemetry.addData("Derivative ", rotationPID.derivative);
            Op.telemetry.addData("Integral ", rotationPID.integral);
            Op.telemetry.addData("TD ", rotationPID.deltaTime.seconds());
            Op.telemetry.addData("Rotation ", rotation);
            Op.telemetry.addData("rotationPower ", rotationPower);
            Op.telemetry.addData("rotationSpeed ", rotationSpeed);
            Op.telemetry.addData("time ", timer);
            Op.telemetry.update();

            rotateSimple(rotationPower);

            //Exit the PID loop if the bots not rotating or is withing 1.25 degrees of the target angle or if we hit the maxtime
//            Math.abs(rotationPower) < 0.1 ||

//            if (bMath.DeltaDegree(rotation, targetAngle) < 1.25 || timer >= maxTime) {
            if (timer >= maxTime || Math.abs(rotationPID.error) < 0.75) {
                break;
            }

            timer += deltaTime.seconds();

            deltaTime.reset();
        }

        setPowerDouble4(0, 0, 0, 0, 0);
    }


    public void rotateSimple(double targetAngle, double rotationSpeed, double tolerance,
                             double exitTime) {
        double exitTimer = 0;
        ElapsedTime deltaTime = new ElapsedTime();


        while (Op.opModeIsActive()) {
            deltaTime.reset();

            double rotation = ((getRotation() - targetAngle) / -180) * rotationSpeed;

//            rotation = bMath.Clamp(1)

            double clampedRotation = rotation + (rotation > 0 ? 0.2 : -0.2);

            rotateSimple(clampedRotation);

            Op.telemetry.addData("rotation delta ", getRotation() - targetAngle);
            Op.telemetry.addData("cur rotation ", getRotation());
            Op.telemetry.addData("goal rotation ", targetAngle);
            Op.telemetry.addData("exit Timer", exitTime);
            Op.telemetry.update();

            if (Math.abs(getRotation() - targetAngle) < tolerance) {
                exitTimer += deltaTime.seconds();
            }

            if (exitTimer > exitTime) {
                break;
            }
        }

        setPowerDouble4(0, 0, 0, 0, 0);
    }

    /**
     * @param v          this is the vector that represents our wheels power! Create a new Double4 like so:
     *                   new Double4(x,y,z,w)
     *                   <p>
     *                   See RobotConfiguration for more information
     * @param multiplier the coefficient of 'v'
     */

    public void setPowerDouble4(Double4 v, double multiplier) {
        driveManager.frontLeft.setPower(v.x * multiplier);
        driveManager.frontRight.setPower(v.y * multiplier);
        driveManager.backLeft.setPower(v.z * multiplier);
        driveManager.backRight.setPower(v.w * multiplier);
    }

    public void SetPersistentVector(Double2 vector, double imu) {

    }

    public void SetPersistentRotation(double relativeAngle) {
    }


    //Front left power is the X value
    //Front right power is the Y value
    //Back left power is the Z value
    //Back right power is the W value
    public void setPowerDouble4(double x, double y, double z, double w, double multiplier) {
        Double4 v = new Double4(x, y, z, w);

        driveManager.frontLeft.setPower(v.x * multiplier);
        driveManager.frontRight.setPower(v.y * multiplier);
        driveManager.backLeft.setPower(v.z * multiplier);
        driveManager.backRight.setPower(v.w * multiplier);

//        backRight.setPower(v.x * multiplier);
//        frontLeft.setPower(v.w * multiplier);
//        frontRight.setPower(v.z * multiplier);
//        backLeft.setPower(v.y * multiplier);
    }

    public void setDriveMode(DcMotor.RunMode mode) {
        driveManager.frontLeft.setMode(mode);
        driveManager.backLeft.setMode(mode);
        driveManager.frontRight.setMode(mode);
        driveManager.backRight.setMode(mode);
    }

    public void setRelativeEncoderPosition(double delta) {

        driveManager.frontLeft.setTargetPosition(driveManager.frontLeft.getCurrentPosition() + (int) delta);
        driveManager.backLeft.setTargetPosition(driveManager.backLeft.getCurrentPosition() + (int) delta);
        driveManager.frontRight.setTargetPosition(driveManager.frontRight.getCurrentPosition() + (int) delta);
        driveManager.backRight.setTargetPosition(driveManager.backRight.getCurrentPosition() + (int) delta);
    }

    public void setRelativeEncoderPosition(double deltaX, double deltaY, double deltaZ,
                                           double deltaW) {

        driveManager.frontLeft.setTargetPosition(driveManager.frontLeft.getCurrentPosition() + (int) deltaX);
        driveManager.backLeft.setTargetPosition(driveManager.backLeft.getCurrentPosition() + (int) deltaY);
        driveManager.frontRight.setTargetPosition(driveManager.frontRight.getCurrentPosition() + (int) deltaZ);
        driveManager.backRight.setTargetPosition(driveManager.backRight.getCurrentPosition() + (int) deltaW);
    }

    //Returns IMU rotation on the zed axies
    public double getRotation() {
        //returns the threaded rotation values for speeeed
        return rotation;
    }

    //Returns true if any wheels are currently busy
    public boolean wheelsBusy() {
        return driveManager.frontRight.isBusy() || driveManager.frontLeft.isBusy() || driveManager.backLeft.isBusy() || driveManager.backRight.isBusy();
//        return driveManager.frontRight.isBusy() || driveManager.frontLeft.isBusy() || driveManager.backLeft.isBusy() || driveManager.backRight.isBusy();
    }
    //</editor-fold>

    //Drive forward a set distance at a set speed, distance is measured in CM
    public void driveByDistance(double speed, double distance) {

        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setRelativeEncoderPosition((480 / RobotConfiguration.wheel_circumference) * distance);
        setPowerDouble4(1, 1, 1, 1, speed);
        setDriveMode(DcMotor.RunMode.RUN_TO_POSITION);

        Op.telemetry.addData("Driving by distance ", distance * ((RobotConfiguration.wheel_circumference * RobotConfiguration.wheel_ticksPerRotation)));
        Op.telemetry.update();
        while (Op.opModeIsActive() && wheelsBusy()) {
            Op.telemetry.addData("Wheel Busy", "");
            Op.telemetry.addData("Wheel Front Right Postion", driveManager.frontRight.getCurrentPosition());
            Op.telemetry.addData("Wheel Front Right Target", driveManager.frontRight.motor.getTargetPosition());
            Op.telemetry.update();

            if (!Op.opModeIsActive()) {
                break;
            }
            //Wait until we are at our target distance
        }

        Op.telemetry.addData("Target Reached", "");
        Op.telemetry.update();

        //shutdown motors
        setPowerDouble4(0, 0, 0, 0, 0);

        //Set up for normal driving
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    public void driveByDistance(double angle, double speed, double distance) {

        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        double distanceTicks = (480 / RobotConfiguration.wheel_circumference) * distance;
        Double4 a = bMath.getMecMovement(angle, 0, 0);

        setRelativeEncoderPosition(a.x * distanceTicks, a.y * distanceTicks, a.z * distanceTicks, a.w * distanceTicks);
        setPowerDouble4(1, 1, 1, 1, speed);

//        setRelativeEncoderPosition(a.x * distanceTicks, a.y * distanceTicks, a.z * distanceTicks, a.w * distanceTicks);
//        setPowerDouble4(a.x, a.y, a.z, a.w, speed);


        setDriveMode(DcMotor.RunMode.RUN_TO_POSITION);

        Op.telemetry.addData("Driving by distance ", distance * ((RobotConfiguration.wheel_circumference * RobotConfiguration.wheel_ticksPerRotation)));
        Op.telemetry.update();
        while (Op.opModeIsActive() && wheelsBusy()) {
            Op.telemetry.addData("Wheel Busy", "");
            Op.telemetry.addData("Wheel Front Right Postion", driveManager.frontRight.getCurrentPosition());
            Op.telemetry.addData("Wheel Front Right Target", driveManager.frontRight.motor.getTargetPosition());
            Op.telemetry.update();

            if (!Op.opModeIsActive()) {
                break;
            }
            //Wait until we are at our target distance
        }

        Op.telemetry.addData("Target Reached", "");
        Op.telemetry.update();

        //shutdown motors
        setPowerDouble4(0, 0, 0, 0, 0);

        //Set up for normal driving
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }


    public void driveByDistance(double angle, double speed, double distance, double maxTime) {

        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        double distanceTicks = (480 / RobotConfiguration.wheel_circumference) * distance;

        double runTime = 0;
        ElapsedTime deltaTime = new ElapsedTime();

        Double4 a = bMath.getMecMovement(angle, 0, 0);

        setRelativeEncoderPosition(a.x * distanceTicks, a.y * distanceTicks, a.z * distanceTicks, a.w * distanceTicks);
        setPowerDouble4(1, 1, 1, 1, speed);

//        setRelativeEncoderPosition(a.x * distanceTicks, a.y * distanceTicks, a.z * distanceTicks, a.w * distanceTicks);
//        setPowerDouble4(a.x, a.y, a.z, a.w, speed);


        setDriveMode(DcMotor.RunMode.RUN_TO_POSITION);

        Op.telemetry.addData("Driving by distance ", distance * ((RobotConfiguration.wheel_circumference * RobotConfiguration.wheel_ticksPerRotation)));
        Op.telemetry.update();
        deltaTime.reset();
        while (Op.opModeIsActive() && wheelsBusy()) {
            Op.telemetry.addData("Wheel Busy", "");
            Op.telemetry.addData("Wheel Front Right Postion", driveManager.frontRight.getCurrentPosition());
            Op.telemetry.addData("Wheel Front Right Target", driveManager.frontRight.motor.getTargetPosition());
            Op.telemetry.update();

            runTime += deltaTime.seconds();

            if (!Op.opModeIsActive() || runTime > maxTime) {
                break;
            }

            deltaTime.reset();
        }

        Op.telemetry.addData("Target Reached", "");
        Op.telemetry.update();

        //shutdown motors
        setPowerDouble4(0, 0, 0, 0, 0);

        //Set up for normal driving
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    /*
    keep driveSpeed ~ 1 for max speed (high is good)
    attackSpeed ~ 0.3 and definitly smaller than driveSpeed (high is good)
    decaySpeed ~ 0.1 and definitly smaller than attackSpeed (high is good
    correctionAngle = the angle you want the robot to be at, should be what it's current rotation of the robot
    distanceExitThreshold is in encoder ticks = inaccuracy allowed
     */

    public void  experimentalDriveByDistance(double driveHeading, double driveSpeed,
                                            double attackSpeed, double decaySpeed, double correctionAngle, double distance, int distanceExitThreshold) {
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);

        double distanceTicks = (480 / RobotConfiguration.wheel_circumference) * distance;
        double percentComplete = 0;

        while (Op.opModeIsActive()) {

            if (Math.abs(((double) totalWheelEncoderTicks() / 4) - distanceTicks) < distanceExitThreshold) {
                break;
            }

            percentComplete = ((double) totalWheelEncoderTicks() / 4) / distanceTicks;

            moveComplex(driveHeading, (Math.sin(percentComplete * Math.PI) * driveSpeed) + bMath.Lerp(attackSpeed, decaySpeed, percentComplete), getRotation() - correctionAngle, 0);
        }
        stopDrive();
    }

    int totalWheelEncoderTicks() {
        return driveManager.backRight.getCurrentPosition() + driveManager.backLeft.getCurrentPosition() + driveManager.frontLeft.getCurrentPosition() + driveManager.frontRight.getCurrentPosition();
    }

//    public void experimentalDriveByDistanceWithRotationYeahItsPrettyCo0o0oOo0OoO0Oool(double driveHeading,
//                                                                                      double driveSpeed, double initalSpeed, double initalAngle, double finalAngle,
//                                                                                      double distance) {
//        driveManager.backRight.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
//        driveManager.backRight.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
//
//        double distanceTicks = (480 / RobotConfiguration.wheel_circumference) * distance;
//
//        double speedAdd = initalSpeed;
//
//        double percentComplete = 0;
//
//        while (driveManager.backRight.getCurrentPosition() < distanceTicks) {
//
//            percentComplete = driveManager.backRight.getCurrentPosition() / distanceTicks;
//
//            moveComplex(getRotation() - driveHeading, (Math.sin(percentComplete * Math.PI) * driveSpeed) + initalSpeed, getRotation() - (percentComplete > 0.5 ? finalAngle : initalAngle), 0);
////            moveComplex(driveHeading, driveSpeed, getRotation() - correctionAngle, 0);
//        }
//        stopDrive();
//    }


    public void stopDrive() {
        setPowerDouble4(0, 0, 0, 0, 0);
    }


    @Deprecated
    public enum simpleDirection {
        FORWARD,
        BACKWARD,
        RIGHT,
        LEFT;
    }

    // can go forward, backwards, or sideways
    //distance should be in cm
    @Deprecated
    public void driveByDistancePoorly(double distance, simpleDirection direction,
                                      double speedMultiplier) {
        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        int targetEncoders = (int) ((480.0 / RobotConfiguration.wheel_circumference) * distance);
        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);

        if (direction == simpleDirection.FORWARD) { //if you wanna go forward, this is the stuff
            setPowerDouble4(1, 1, 1, 1, speedMultiplier);
            while (Op.opModeIsActive() && driveManager.backLeft.getCurrentPosition() < 0.5 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.5 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(1, 1, 1, 1, 0.5 * speedMultiplier);
            while (Op.opModeIsActive() && driveManager.backLeft.getCurrentPosition() < 0.75 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.75 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(1, 1, 1, 1, 0.25 * speedMultiplier);
            while (Op.opModeIsActive() && driveManager.backLeft.getCurrentPosition() < targetEncoders && driveManager.backRight.getCurrentPosition() < targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(0, 0, 0, 0, 0);
        }

        if (direction == simpleDirection.LEFT) {
            setPowerDouble4(-1, 1, 1, -1, 1);
            while (driveManager.backLeft.getCurrentPosition() < 0.5 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.5 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, 1, 1, -1, 0.5);
            while (driveManager.backLeft.getCurrentPosition() < 0.75 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.75 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, 1, 1, -1, 0.25);
            while (driveManager.backLeft.getCurrentPosition() < targetEncoders && driveManager.backRight.getCurrentPosition() < targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, 1, 1, -1, 0);
        }

        if (direction == simpleDirection.BACKWARD) ;
        {
            setPowerDouble4(-1, -1, -1, -1, 1);
            while (driveManager.backLeft.getCurrentPosition() < 0.5 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.5 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, -1, -1, -1, 0.5);
            while (driveManager.backLeft.getCurrentPosition() < 0.75 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.75 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, -1, -1, -1, 0.25);
            while (driveManager.backLeft.getCurrentPosition() < targetEncoders && driveManager.backRight.getCurrentPosition() < targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(-1, -1, -1, -1, 0);
        }

        if (direction == simpleDirection.RIGHT) ;
        {
            setPowerDouble4(1, -1, -1, 1, 1);
            while (driveManager.backLeft.getCurrentPosition() < 0.5 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.5 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(1, -1, -1, 1, 0.5);
            while (driveManager.backLeft.getCurrentPosition() < 0.75 * targetEncoders && driveManager.backRight.getCurrentPosition() < 0.75 * targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(1, -1, -1, 1, 0.25);
            while (driveManager.backLeft.getCurrentPosition() < targetEncoders && driveManager.backRight.getCurrentPosition() < targetEncoders) {
            } //empty while loop works as waitUntil command
            setPowerDouble4(1, -1, -1, 1, 0);
        }
    }


    //This version of drive by distance doesnt use Drive To Position and keeps the oriantation the same
    //WIP

    //3666 X
    //11577
    //16884
//    public void driveByDistance(double speed, double distance, double targetRotation) {
//
//        double distances = (480 / RobotConfiguration.wheel_circumference) * distance;
//        Double4 targetDistances = new Double4(distances, distances, distances, distances);
//
//        setPowerDouble4(1, 1, 1, 1, speed);
//        while (op.opModeIsActive() && driveManager.backRight > targetDistances.x) {
//            setPowerDouble4();
//
//
//            if (!op.opModeIsActive()) {
//                break;
//            }
//        }
//
//        op.telemetry.addData("Target Reached", "");
//        op.telemetry.update();
//
//        //shutdown motors
//        setPowerDouble4(0, 0, 0, 0, 0);
//
//        //Set up for normal driving
//        setDriveMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
//        setDriveMode(DcMotor.RunMode.RUN_USING_ENCODER);
//    }

    //Returns the distance using a sensor group
    public double getDistance(RobotWallTrack.groupID group, DistanceUnit unit) {
        return wallTrack.sensorIDGroupPairs.get(group).getDistanceAverage(unit);
    }

    //Sets the position of the foundation 'arm' servos,
    //Setting this value to 0 will raise the arms all of the way
    //Setting this value to 0.73 will lower the arms without impacting wheels
    //Setting this value to 1 will lower the arms and strain on the wheels, should only be used when dragging the foundation
    public void setFoundationGripperState(double value) {
        foundationServo0.setPosition(1 - value);
        foundationServo1.setPosition(value);
    }

    //grips the foundation, meant to be human readable (by judges)
<<<<<<< HEAD
    public void gripFoundation(){
        foundationServo0.setPosition(1);
        foundationServo1.setPosition(0);
=======
    public void gripFoundation() {
        setFoundationGripperState(0);
>>>>>>> 0f1b00d1fd33af676cd0e38e9ac358dd612b70c1
    }

    //lets go of the foundation, meant to be human readable (by judges)
    public void releaseFoundation() {
<<<<<<< HEAD
        foundationServo0.setPosition(0);
        foundationServo1.setPosition(1);
    }
=======
        setFoundationGripperState(0.9);
    }

    // Drive Helper Method
    public void updateRobotDrive(double frontLeft, double frontRight, double backLeft,
                                 double backRight) {
        driveManager.frontLeft.setPower(bMath.Clamp(frontLeft, -1, 1));
        driveManager.frontRight.setPower(bMath.Clamp(frontRight, -1, 1));
        driveManager.backLeft.setPower(bMath.Clamp(backLeft, -1, 1));
        driveManager.backRight.setPower(bMath.Clamp(backRight, -1, 1));
    }

    //Very similer to "updateRobotDrive" but the order of the variables makes more sense to some team members
    public void setWheelPowersInAClockwiseOrder(double frontLeft, double frontRight, double backRight, double backLeft) {
        driveManager.frontLeft.setPower(frontLeft);
        driveManager.frontRight.setPower(frontRight);
        driveManager.backLeft.setPower(backLeft);
        driveManager.backRight.setPower(backRight);
    }

    public Vector2 getMovementVector(Gamepad gamepad,
                                     double rotationLockAngle,
                                     double movementInput_x,
                                     double movementInput_y) {
        Vector2 result = new Vector2(0, 0);

        double angle = Math.toRadians(getRotation() - rotationLockAngle);
        double movementSpeed = (Math.sqrt(Math.pow(movementInput_x, 2) + Math.pow(movementInput_y, 2)));

        double _newGamepadX = movementSpeed * Math.cos(angle + Math.atan(movementInput_y / movementInput_x));
        double _newGamepadY = movementSpeed * Math.sin(angle + Math.atan(movementInput_y / movementInput_x));

        result.x = (gamepad.left_stick_x <= 0) ? -_newGamepadX : _newGamepadX;
        result.y = (gamepad.left_stick_x <= 0) ? -_newGamepadY : _newGamepadY; // **** IS `left_stick_x` a BUG **** shouldn't it be y ???
        return result;
    }


>>>>>>> 0f1b00d1fd33af676cd0e38e9ac358dd612b70c1
}
