package com.mattworzala.debug.client;

import com.mattworzala.debug.client.render.ClientRenderer;
import com.mattworzala.debug.client.shape.Shape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class DebugRendererClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final int PROTOCOL_VERSION = 2;
    public static final Identifier PACKET_ID = new Identifier("debug", "shapes");

    private final ClientRenderer renderer = new ClientRenderer();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Waiting to render debug shapes!");

        // Setup listeners
        ClientPlayConnectionEvents.JOIN.register(this::handleJoinGame);
        ClientPlayConnectionEvents.DISCONNECT.register(this::handleDisconnect);

        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::handleRenderFabulous);
        WorldRenderEvents.LAST.register(this::handleRenderLast);

        // Networking
        ClientPlayNetworking.registerGlobalReceiver(PACKET_ID, this::handlePacket);

        // Test main if present
        try {
            var testMain = Class.forName("com.mattworzala.debug.test.DebugRendererTest");
            testMain.getMethod("init", ClientRenderer.class).invoke(null, renderer);
            LOGGER.info("Initialized test main");
        } catch (ClassNotFoundException ignored) {
            // If class not found we are probably not running test source sets, so just move on.
            LOGGER.warn("Failed to find test main");
        } catch (Exception e) {
            LOGGER.error("Failed to init test main", e);
        }

    }

    private void handleRenderFabulous(WorldRenderContext ctx) {
        if (!ctx.advancedTranslucency()) return;

        try {
            RenderSystem.getModelViewStack().push();
//            RenderSystem.getModelViewStack().loadIdentity();
//            RenderSystem.getModelViewStack().multiplyPositionMatrix(ctx.matrixStack().peek().getPositionMatrix());
//            RenderSystem.applyModelViewMatrix();
            ctx.worldRenderer().getTranslucentFramebuffer().beginWrite(false);
            renderer.render(ctx.matrixStack().peek().getPositionMatrix());
        } finally {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
            RenderSystem.getModelViewStack().pop();
        }
    }

    private void handleRenderLast(WorldRenderContext ctx) {
        if (ctx.advancedTranslucency()) return;
        renderer.render(ctx.matrixStack().peek().getPositionMatrix());
    }

    private void handleJoinGame(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        var data = PacketByteBufs.create();
        data.writeInt(PROTOCOL_VERSION);
        ClientPlayNetworking.send(new Identifier("debug", "hello"), data);
    }

    private void handleDisconnect(ClientPlayNetworkHandler handler, MinecraftClient client) {
        renderer.clear();
    }

    private void handlePacket(@NotNull MinecraftClient client, @NotNull ClientPlayNetworkHandler handler,
                              @NotNull PacketByteBuf buffer, @NotNull PacketSender sender) {
        System.out.println("Received packet");
        var opCount = buffer.readVarInt();
        System.out.println("Received " + opCount + " operations");
        for (int i = 0; i < opCount; i++) {
            int op = buffer.readVarInt();
            System.out.println("Operation " + i + ": " + op);
            switch (op) {
                case 0 -> { // SET
                    var shapeId = buffer.readIdentifier();
                    System.out.println(" Shape id: " + shapeId);
                    var shapeType = buffer.readEnumConstant(Shape.Type.class);
                    System.out.println(" Shape type: " + shapeType);
                    var deserialized = shapeType.deserialize(buffer);
                    renderer.add(shapeId, deserialized);
                    System.out.println(" Added shape: " + deserialized);
                }
                case 1 -> { // REMOVE
                    Identifier targetShape = buffer.readIdentifier();
                    System.out.println(" Remove shape: " + targetShape);
                    renderer.remove(targetShape);
                }
                case 2 -> { // CLEAR_NS
                    String targetNamespace = buffer.readString(32767);
                    System.out.println(" Clear namespace: " + targetNamespace);
                    renderer.remove(targetNamespace);
                }
                case 3 -> { // CLEAR
                    System.out.println(" Clear all");
                    renderer.clear();
                }
            }
        }
    }

}
