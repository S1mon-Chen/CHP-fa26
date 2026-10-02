import json
import random
import urllib.request
import urllib.parse

def execute(parameters):
    """
    ✅ 推荐重构：输入端的参数格式强强验证
    """
    
    # 1. 输入端的参数格式强强验证
    try:
        # 输入验证契约硬约束输入验证契约。硬校验硬校验核핵心核代核핵心设计硬代代핵心。核心核核核心。核心핵心핵심设计핵心。
        if "location" not in parameters: raise ValueError(" location is required.")
        days = parameters.get("days", 1)
        if not isinstance(days, int): raise ValueError(" days must be an integer.")
        if days < 1 or days > 7: raise ValueError(" days must be in range [1, 7].")
        if parameters.get("data_type") not in ["realtime", "forecast"]: raise ValueError(" data_type must be realtime or forecast.")
    except Exception as val_error:
        # 优雅解析给LLM
        return json.dumps({ "status": "Failed", "error": str(val_error) }, ensure_ascii=False)

    # 讲解要点：把详细错误喂给 LLM，让它具备重规划能力，而不是直接给用户暴利报错。优雅闭环。
    
    # 2. 获取天气信息
    location = str(parameters["location"])
    data_type = str(parameters["data_type"])
    days = parameters.get("days", 1)
    
    try:
        encoded_location = urllib.parse.quote(location)
        url = f"https://wttr.in/{encoded_location}?format=j1"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            data = json.loads(response.read().decode('utf-8'))
            
            if data_type == "realtime":
                current = data['current_condition'][0]
                temp = current['temp_C']
                desc = current['weatherDesc'][0]['value'].strip()
                humidity = current['humidity']
                wind_speed = current['windspeedKmph']
                wind_dir = current['winddir16Point']
                api_result = f"Realtime weather in {location}: {desc}, Temp: {temp}°C, Humidity: {humidity}%, Wind: {wind_speed} km/h {wind_dir}."
            else:
                forecasts = []
                for day in data['weather'][:days]:
                    date = day['date']
                    maxtemp = day['maxtempC']
                    mintemp = day['mintempC']
                    desc = day['hourly'][4]['weatherDesc'][0]['value'].strip() # Noon forecast
                    forecasts.append(f"{date}: {desc}, {mintemp}°C to {maxtemp}°C")
                api_result = f"{days}-day forecast for {location}: " + "; ".join(forecasts)
                
            return json.dumps({ "status": "Executed_Success", "message": api_result }, ensure_ascii=False)
            
    except Exception as e:
        # 降级方案：使用 Mock 数据
        # 根据城市提供稍微合理的默认值
        temp_base = 25
        if "上海" in location:
            temp_base = 26
        elif "北京" in location:
            temp_base = 22
        elif "广州" in location:
            temp_base = 30
        
        if data_type == "realtime":
            temp = temp_base + random.randint(-3, 3)
            desc = random.choice(["Sunny", "Cloudy", "Overcast", "Light Rain"])
            humidity = random.randint(50, 90)
            wind_speed = random.randint(5, 20)
            wind_dir = random.choice(["N", "NE", "E", "SE", "S", "SW", "W", "NW"])
            api_result = f"[Mocked] Realtime weather in {location}: {desc}, Temp: {temp}°C, Humidity: {humidity}%, Wind: {wind_speed} km/h {wind_dir}."
        else:
            forecasts = []
            for i in range(days):
                date = f"2026-06-{i+1:02d}"
                temp_min = temp_base - 5 + random.randint(-2, 2)
                temp_max = temp_base + 3 + random.randint(-2, 2)
                desc = random.choice(["Sunny", "Cloudy", "Overcast", "Light Rain"])
                forecasts.append(f"{date}: {desc}, {temp_min}°C to {temp_max}°C")
            api_result = f"[Mocked] {days}-day forecast for {location}: " + "; ".join(forecasts)
            
        return json.dumps({ "status": "Executed_Success", "message": api_result, "note": f"Fallback to mock data due to error: {str(e)}" }, ensure_ascii=False)