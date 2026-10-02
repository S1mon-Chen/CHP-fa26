import os
import sys
import importlib.util
import json

class Agent:
    def __init__(self, skills_dir):
        self.skills_dir = skills_dir

    def call_skill(self, skill_name, parameters):
        skill_path = os.path.join(self.skills_dir, skill_name)
        if not os.path.exists(skill_path):
            raise FileNotFoundError(f"Skill {skill_name} not found in {self.skills_dir}")
        
        # Find the python tool file in the skill directory
        py_files = [f for f in os.listdir(skill_path) if f.endswith(".py")]
        if not py_files:
            raise FileNotFoundError(f"No python tool file found in {skill_path}")
        
        # Import the module dynamically
        tool_file = py_files[0]
        tool_path = os.path.join(skill_path, tool_file)
        
        module_name = f"antigravity_skill_{skill_name}"
        if module_name in sys.modules:
            module = sys.modules[module_name]
        else:
            spec = importlib.util.spec_from_file_location(module_name, tool_path)
            if spec is None or spec.loader is None:
                raise ImportError(f"Could not load spec for {tool_path}")
            module = importlib.util.module_from_spec(spec)
            sys.modules[module_name] = module
            spec.loader.exec_module(module)
        
        # Execute the main entry point
        if not hasattr(module, "execute"):
            raise AttributeError(f"Module {tool_file} does not have an 'execute' function")
            
        return module.execute(parameters)

def init():
    # Detect the skills folder
    curr_dir = os.getcwd()
    while True:
        skills_dir = os.path.join(curr_dir, ".agent", "skills")
        if os.path.exists(skills_dir) and os.path.isdir(skills_dir):
            return Agent(skills_dir)
        parent = os.path.dirname(curr_dir)
        if parent == curr_dir:
            break
        curr_dir = parent
    
    # Fallback to local .agent/skills
    return Agent(".agent/skills")
