bl_info = {
    "name": "CineMotion Remote - Blender Bridge",
    "author": "CineMotion Studio",
    "version": (1, 0, 0),
    "blender": (3, 6, 0),
    "location": "View3D > Sidebar > CineMotion",
    "description": "6DoF Real-time Virtual Camera Rig receiver for CineMotion Remote Android App",
    "category": "Animation",
}

import bpy
import asyncio
import json
import socket
import threading

# Global state for server
SERVER_THREAD = None
SERVER_LOOP = None
IS_SERVER_RUNNING = False
CONNECTED_CLIENTS = set()

def get_local_ip():
    """Find the local Wi-Fi / LAN IP of the workstation"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def update_blender_camera(data):
    """Applies 6DoF position, quaternion rotation, and lens focal length"""
    scene = bpy.context.scene
    cam = scene.camera
    if not cam:
        return

    # 1. 6DoF Translation [X, Y, Z]
    # ARCore: X=Right, Y=Up, Z=Back. Blender: X=Right, Y=Depth, Z=Height
    pos = data.get("pos", [0, 0, 0])
    cam.location.x = pos[0]
    cam.location.y = -pos[2]
    cam.location.z = pos[1]

    # 2. Quaternion Rotation [qx, qy, qz, qw]
    quat = data.get("quat", [0, 0, 0, 1])
    cam.rotation_mode = 'QUATERNION'
    cam.rotation_quaternion = (quat[3], quat[0], -quat[2], quat[1])

    # 3. Lens Focal Length (mm)
    if "lens" in data and hasattr(cam.data, "lens"):
        cam.data.lens = data["lens"]

async def ws_handler(websocket):
    CONNECTED_CLIENTS.add(websocket)
    scene = bpy.context.scene
    target_name = scene.cinemotion_props.target_object_name or "Character_01"
    
    # Send target name to phone
    await websocket.send(json.dumps({"type": "target", "name": target_name}))

    try:
        async for message in websocket:
            try:
                msg = json.loads(message)
                m_type = msg.get("type")

                if m_type == "ping":
                    # Respond to ping to measure latency
                    await websocket.send(json.dumps({"type": "pong", "t": msg.get("t", 0)}))

                elif m_type == "pose":
                    # Real-time live operator pose
                    update_blender_camera(msg)

                elif m_type == "bake":
                    # Bake all take samples into scene keyframes
                    take = msg.get("take", {})
                    samples = take.get("samples", [])
                    cam = bpy.context.scene.camera
                    fps = bpy.context.scene.render.fps
                    
                    for s in samples:
                        frame_num = int((s["t"] / 1000.0) * fps) + 1
                        update_blender_camera(s)
                        cam.keyframe_insert(data_path="location", frame=frame_num)
                        cam.keyframe_insert(data_path="rotation_quaternion", frame=frame_num)
            except Exception:
                pass
    finally:
        CONNECTED_CLIENTS.discard(websocket)

def run_server(host, port):
    global SERVER_LOOP, IS_SERVER_RUNNING
    try:
        import websockets
    except ImportError:
        import subprocess, sys
        subprocess.run([sys.executable, "-m", "pip", "install", "websockets"])
        import websockets

    SERVER_LOOP = asyncio.new_event_loop()
    asyncio.set_event_loop(SERVER_LOOP)
    server = websockets.serve(ws_handler, host, port)
    SERVER_LOOP.run_until_complete(server)
    IS_SERVER_RUNNING = True
    SERVER_LOOP.run_forever()

class CINEMOTION_Properties(bpy.types.PropertyGroup):
    port: bpy.props.IntProperty(
        name="Port",
        description="WebSocket Server Port",
        default=8765,
        min=1024,
        max=65535
    )
    target_object_name: bpy.props.StringProperty(
        name="Target Object",
        description="Name of the target actor or object to track",
        default="Character_01"
    )

class CINEMOTION_OT_StartServer(bpy.types.Operator):
    bl_idname = "cinemotion.start_server"
    bl_label = "Start CineMotion Bridge"
    bl_description = "Starts the WebSocket server for the CineMotion phone controller"

    def execute(self, context):
        global SERVER_THREAD, IS_SERVER_RUNNING
        if IS_SERVER_RUNNING:
            self.report({'INFO'}, "CineMotion Bridge is already running")
            return {'FINISHED'}

        props = context.scene.cinemotion_props
        SERVER_THREAD = threading.Thread(
            target=run_server,
            args=("0.0.0.0", props.port),
            daemon=True
        )
        SERVER_THREAD.start()
        IS_SERVER_RUNNING = True
        self.report({'INFO'}, f"CineMotion listening on {get_local_ip()}:{props.port}")
        return {'FINISHED'}

class CINEMOTION_OT_StopServer(bpy.types.Operator):
    bl_idname = "cinemotion.stop_server"
    bl_label = "Stop Bridge"
    bl_description = "Stops the CineMotion WebSocket server"

    def execute(self, context):
        global SERVER_LOOP, IS_SERVER_RUNNING
        if SERVER_LOOP:
            SERVER_LOOP.call_soon_threadsafe(SERVER_LOOP.stop)
        IS_SERVER_RUNNING = False
        self.report({'INFO'}, "CineMotion Bridge stopped")
        return {'FINISHED'}

class CINEMOTION_PT_SidebarPanel(bpy.types.Panel):
    bl_label = "CineMotion Remote"
    bl_idname = "CINEMOTION_PT_sidebar"
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = 'CineMotion'

    def draw(self, context):
        layout = self.layout
        props = context.scene.cinemotion_props
        local_ip = get_local_ip()

        # Connection Box
        box = layout.box()
        box.label(text="Local Connection Info", icon='RADIO')
        col = box.column(align=True)
        col.label(text=f"Computer IP: {local_ip}")
        col.prop(props, "port")
        col.prop(props, "target_object_name")

        # Status & Toggle Button
        if IS_SERVER_RUNNING:
            box.label(text=f"Status: ONLINE ({len(CONNECTED_CLIENTS)} Phone Connected)", icon='CHECKMARK')
            box.operator("cinemotion.stop_server", text="Stop Server", icon='CANCEL')
        else:
            box.label(text="Status: OFFLINE", icon='ERROR')
            box.operator("cinemotion.start_server", text="Start Bridge", icon='PLAY')

        # Camera Status Box
        cam_box = layout.box()
        cam_box.label(text="Active Camera Rig", icon='CAMERA_DATA')
        if context.scene.camera:
            cam_box.label(text=f"Camera: {context.scene.camera.name}", icon='VIEW_CAMERA')
        else:
            cam_box.label(text="No active camera in scene!", icon='ERROR')

classes = (
    CINEMOTION_Properties,
    CINEMOTION_OT_StartServer,
    CINEMOTION_OT_StopServer,
    CINEMOTION_PT_SidebarPanel,
)

def register():
    for cls in classes:
        bpy.utils.register_class(cls)
    bpy.types.Scene.cinemotion_props = bpy.props.PointerProperty(type=CINEMOTION_Properties)

def unregister():
    for cls in reversed(classes):
        bpy.utils.unregister_class(cls)
    del bpy.types.Scene.cinemotion_props

if __name__ == "__main__":
    register()
