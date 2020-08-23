package blue.endless.glow;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.ARBTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.playsawdust.chipper.glow.RenderScheduler;
import com.playsawdust.chipper.glow.Window;
import com.playsawdust.chipper.glow.control.DigitalButtonControl;
import com.playsawdust.chipper.glow.control.MouseLook;
import com.playsawdust.chipper.glow.gl.shader.ShaderError;
import com.playsawdust.chipper.glow.gl.shader.ShaderIO;
import com.playsawdust.chipper.glow.gl.shader.ShaderProgram;
import com.playsawdust.chipper.glow.mesher.TextMesher;
import com.playsawdust.chipper.glow.mesher.VoxelMesher;
import com.playsawdust.chipper.glow.gl.BakedModel;
import com.playsawdust.chipper.glow.gl.LightTexture;
import com.playsawdust.chipper.glow.gl.Texture;
import com.playsawdust.chipper.glow.model.Material;
import com.playsawdust.chipper.glow.model.MaterialAttribute;
import com.playsawdust.chipper.glow.model.Mesh;
import com.playsawdust.chipper.glow.model.Model;
import com.playsawdust.chipper.glow.model.SimpleMaterialAttributeContainer;
import com.playsawdust.chipper.glow.model.Material.Generic;
import com.playsawdust.chipper.glow.model.io.OBJLoader;
import com.playsawdust.chipper.glow.pass.MeshPass;
import com.playsawdust.chipper.glow.scene.Light;
import com.playsawdust.chipper.glow.scene.MeshActor;
import com.playsawdust.chipper.glow.scene.Scene;
import com.playsawdust.chipper.glow.voxel.MeshableVoxel;
import com.playsawdust.chipper.glow.voxel.VoxelPatch;
import com.playsawdust.chipper.glow.voxel.VoxelShape;

public class GlowTest {
	private static final int PATCH_SIZE = 128;
	
	private static final double SPEED_LIMIT = 0.2;
	private static final double SPEED_FORWARD = 0.2;
	private static final double SPEED_STRAFE = 0.15;
	
	private static boolean windowSizeDirty = false;
	private static int windowWidth = 0;
	private static int windowHeight = 0;
	private static int mouseX = 0;
	private static int mouseY = 0;
	private static boolean grab = false;
	private static Vector3d cameraPosition = new Vector3d();
	
	public static void main(String... args) {
		
		/* Load up asset(s) */
		
		VoxelPatch patch = generate();
		Model patchModel = VoxelMesher.mesh(0, 0, 0, PATCH_SIZE, 64, PATCH_SIZE, patch::getShape, patch::getMaterial);
		MeshActor patchActor = new MeshActor();
		
		/*
		try(FileOutputStream out = new FileOutputStream("model_save.obj")) {
			OBJLoader.save(patchModel, out);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}*/
		
		MouseLook mouseLook = new MouseLook();
		DigitalButtonControl grabControl = DigitalButtonControl.forKey(GLFW.GLFW_KEY_TAB);
		DigitalButtonControl upControl = DigitalButtonControl.forKey(GLFW.GLFW_KEY_W);
		DigitalButtonControl downControl = DigitalButtonControl.forKey(GLFW.GLFW_KEY_S);
		DigitalButtonControl leftControl = DigitalButtonControl.forKey(GLFW.GLFW_KEY_A);
		DigitalButtonControl rightControl = DigitalButtonControl.forKey(GLFW.GLFW_KEY_D);
		
		/* Start GL, spawn up a window, load and compile the ShaderProgram, and attach it to the solid MeshPass. */
		
		Window.initGLFW();
		
		Window window = new Window(1024, 768, "Test");
		
		GLFW.glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
			if ( key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE )
				GLFW.glfwSetWindowShouldClose(window.handle(), true);
			//System.out.println("Key: "+key+" Code: "+scancode+" Action: "+action+" Mods: "+mods);
			grabControl.handle(key, scancode, action, mods);
			upControl.handle(key, scancode, action, mods);
			downControl.handle(key, scancode, action, mods);
			leftControl.handle(key, scancode, action, mods);
			rightControl.handle(key, scancode, action, mods);
			
		});
		
		GLFW.glfwSetFramebufferSizeCallback(window.handle(), (hWin, width, height)->{
			windowSizeDirty = true;
			windowWidth = width;
			windowHeight = height;
		});
		
		GLFW.glfwSetCursorPosCallback(window.handle(), (hWin, x, y)->{
			mouseX = (int)x;
			mouseY = (int)y;
		});
		
		GLFW.glfwMakeContextCurrent(window.handle());
		
		GL.createCapabilities();
		
		ShaderProgram prog = null;
		RenderScheduler scheduler = RenderScheduler.createDefaultScheduler();
		
		try {
			prog = ShaderIO.load(new FileInputStream(new File("testshader.xml")));
			MeshPass solidPass = (MeshPass) scheduler.getPass("solid");
			solidPass.setShader(prog);
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (ShaderError err) {
			System.out.println(err.getInfoLog());
		}
		
		Model textModel = TextMesher.getModel("Text rendered in-world");
		BakedModel bakedText = scheduler.bake(textModel);
		
		/* Bake Meshes into VertexBuffers, create the LightTexture which will hold our lights */
		//BakedModel bakedModel = scheduler.bake(model);
		BakedModel bakedPatch = scheduler.bake(patchModel);
		
		LightTexture lights = new LightTexture();
		
		Light light = new Light();
		light.setPosition(new Vector3d(0, 3, -1));
		//light.setColor("#fc4");
		light.setIntensity(1.0);
		lights.addLight(light);
		
		Light underLight = new Light();
		underLight.setPosition(0, -0.1, -1.5);
		//underLight.setColor(new Vector3d(1, 0, 1));
		underLight.setIntensity(0.3);
		lights.addLight(underLight);
		
		lights.upload();
		
		BufferedImage none = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		none.setRGB(0, 0, 0xFF_FFFFFF);
		Texture noneTex = Texture.of(none);
		scheduler.registerTexture("none", noneTex);
		
		BufferedImage stoneImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		BufferedImage grassImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		try {
			stoneImage = ImageIO.read(new File("stone.png"));
			grassImage = ImageIO.read(new File("block_face_orange.png"));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		//Texture tex = new Texture();
		Texture orangeTex = Texture.of(grassImage);
		scheduler.registerTexture("orangeDiffuse", orangeTex);
		Texture tex = Texture.of(stoneImage);
		scheduler.registerTexture("stoneDiffuse", tex);
		
		
		patchActor.setRenderModel(bakedPatch);
		patchActor.setPosition(-2, -17, 7);
		//int[] argbData = testImage.getRGB(0, 0, testImage.getWidth(), testImage.getHeight(), new int[testImage.getWidth() * testImage.getHeight()], 0, testImage.getWidth());
		//tex.uploadImage(argbData, testImage.getWidth(), testImage.getHeight());
		
		Scene scene = new Scene();
		scene.addActor(patchActor);
		
		//SimpleMaterialAttributeContainer environment = new SimpleMaterialAttributeContainer();
		//environment.putMaterialAttribute(MaterialAttribute.AMBIENT_LIGHT, new Vector3d(0.15, 0.15, 0.15));
		/* Set the clear color, get the matrices for the model and view, and start the render loop */
		GL11.glClearColor(0.39f, 0.74f, 1.0f, 0.0f);
		
		//Matrix3d rotMatrix = new Matrix3d();
		
		Matrix4d viewMatrix = new Matrix4d();
		viewMatrix.identity();
		viewMatrix.translate(new Vector3d(0,0,0.5));
		
		//long startTime = System.nanoTime() / 1_000_000L;
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		/*
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		if (GL.getCapabilities().GL_ARB_texture_filter_anisotropic) {
			int maxAnisotropy = GL11.glGetInteger(ARBTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY);
			int anisotropy = Math.min(8, maxAnisotropy);
			GL11.glTexParameterf(GL11.GL_TEXTURE_2D, ARBTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY, anisotropy);
		}
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);*/
		GL11.glEnable(GL20.GL_MULTISAMPLE);
		
		GL11.glEnable(GL11.GL_CULL_FACE);
		
		Matrix4d projection = new Matrix4d();
		Vector2d windowSize = new Vector2d();
		window.getSize(windowSize);
		windowWidth = (int) windowSize.x();
		windowHeight = (int) windowSize.y();
		windowSizeDirty = true;
		double SIXTY_DEGREES = 60.0 * (Math.PI/180.0);
		prog.bind();
		
		
		
		while ( !GLFW.glfwWindowShouldClose(window.handle()) ) {
			if (windowSizeDirty) {
				GL11.glViewport(0, 0, windowWidth, windowHeight);
				projection.setPerspective(SIXTY_DEGREES, windowWidth/(double)windowHeight, 0.01, 1000);
				scene.setProjectionMatrix(projection);
			}
			
			if (grabControl.isActive()) {
				grabControl.lock();
				grab = !grab;
				if (grab) {
					GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
				} else {
					GLFW.glfwSetInputMode(window.handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
				}
			}
			if (grab) {
				Vector3d vectorSum = new Vector3d();
				
				if (upControl.isActive()) {
					Vector3d lookVec = mouseLook.getLookVector(null);
					lookVec.mul(SPEED_FORWARD);
					
					vectorSum.add(lookVec);
				}
				
				if (downControl.isActive()) {
					Vector3d lookVec = mouseLook.getLookVector(null);
					lookVec.mul(-SPEED_FORWARD);
					
					vectorSum.add(lookVec);
				}
				
				if (rightControl.isActive()) {
					Vector3d rightVec = mouseLook.getRightVector(null);
					rightVec.mul(SPEED_STRAFE);
					
					vectorSum.add(rightVec);
				}
				
				if (leftControl.isActive()) {
					Vector3d leftVec = mouseLook.getRightVector(null);
					leftVec.mul(-SPEED_STRAFE);
					
					vectorSum.add(leftVec);
				}
				if (vectorSum.length()>SPEED_LIMIT) vectorSum.normalize().mul(SPEED_LIMIT);
				scene.getCamera().setPosition(vectorSum.add(scene.getCamera().getPosition(null)));
			
				mouseLook.step(mouseX, mouseY, windowWidth, windowHeight);
				scene.getCamera().setOrientation(mouseLook.getMatrix());
			}
			
			
			//long now = System.nanoTime() / 1_000_000L;
			//long globalTime = now-startTime;
			
			//double ofs = 2; //Math.sin(globalTime/2_000.0)*2;
			//double ofsy = Math.sin(globalTime/4_000.0)*2;
			
			scene.render(scheduler, prog);
			
			GLFW.glfwSwapBuffers(window.handle());
			
			
			GLFW.glfwPollEvents();
		}
		
		//bakedModel.destroy();
		bakedPatch.destroy();
		tex.destroy();
		prog.destroy();
	}
	
	
	private static VoxelPatch generate() {
		
		VoxelPatch patch = new VoxelPatch();
		patch.setSize(PATCH_SIZE, 64, PATCH_SIZE);
		MeshableVoxel.Block AIR = new MeshableVoxel.Block();
		AIR.setShape(VoxelShape.EMPTY);
		MeshableVoxel.Block STONE = new MeshableVoxel.Block();
		STONE.setShape(VoxelShape.CUBE);
		STONE.setMaterial(new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.5)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "stoneDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0)
			);
		MeshableVoxel.Block ORANGE = new MeshableVoxel.Block();
		ORANGE.setShape(VoxelShape.CUBE);
		ORANGE.setMaterial(new Material.Generic()
			.with(MaterialAttribute.DIFFUSE_COLOR, new Vector3d(1,1,1))
			.with(MaterialAttribute.SPECULARITY, 0.8)
			.with(MaterialAttribute.DIFFUSE_TEXTURE_ID, "orangeDiffuse")
			.with(MaterialAttribute.EMISSIVITY, 0.0)
			);
		
		//Add the voxels to the patch's palette
		patch.setVoxel(0, 0, 0, AIR, true);
		patch.setVoxel(0, 0, 0, STONE, true);
		patch.setVoxel(0, 0, 0, ORANGE, true);
		
		double scroll = Math.random()*16.0;
		for(int z=0; z<PATCH_SIZE; z++) {
			for(int x=0; x<PATCH_SIZE; x++) {
				double materialDensity = Math.sin(x/13.0) + Math.sin(z/17.0);
				//materialDensity = 2;
				
				double dx = 8-x; double dz = 8-z;
				double height = Math.sin(Math.sqrt(dx*dx + dz*dz)/12.0)*4+4;
				double sin = Math.sin((x+scroll)/6.0)*3.0;
				if (sin<0) sin=0;
				for(int y=0; y<Math.min(height+sin+1, 64); y++) {
					MeshableVoxel.Block voxel = (materialDensity>0) ? STONE : ORANGE;
					
					patch.setVoxel(x, y, z, voxel, false);
				}
			}
		}
		
		return patch;
	}
}
