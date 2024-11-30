import json
import yt_dlp
from fp.fp import FreeProxy
import sys
import requests
import os
import time
from datetime import datetime, timedelta
import json5

def read_config(config_path):
    with open(config_path, 'r', encoding='utf-8') as file:
        return json5.load(file)

def get_free_proxy():
    try:
        proxy = FreeProxy(rand=True).get()
        print(f"Testing proxy: {proxy}")
        return proxy
    except Exception as e:
        print(f"Error getting proxy: {e}")
        return None

def is_proxy_working(proxy):
    try:
        proxies = {
            "http": proxy,
            "https": proxy,
        }
        response = requests.get("https://www.example.com", proxies=proxies, timeout=5, verify=False)
        if response.status_code == 200 and "Example Domain" in response.text:
            print(f"Using proxy: {proxy}")
            return True
        else:
            return False
    except Exception as e:
        print(f"Proxy check failed: {e}")
        return False

class SuccessPostProcessor(yt_dlp.postprocessor.common.PostProcessor):
    def run(self, info):
        # Check for the final filename after any conversions.
        final_filepath = info.get('filepath') or info.get('_filename')
        if final_filepath and os.path.exists(final_filepath):
            print(f"Download and conversion successful: {final_filepath}")
            return [], info
        else:
            print("Download or conversion failed.")
            # If the download or conversion failed, raise an exception.
            raise yt_dlp.DownloadError("Download or conversion failed.")

def download_video(url, proxy, ytdlp_options):
    try:
        ytdlp_options['proxy'] = proxy
        ytdlp_options['nocheckcertificate'] = True  # Add this line to ignore certificate checks
        ytdlp_options['verbose'] = True  # Enable verbose output
        # Ensure our custom postprocessor is included in the options.
        with yt_dlp.YoutubeDL(ytdlp_options) as ydl:
            ydl.add_post_processor(SuccessPostProcessor())
            result = ydl.download([url])
            # If the postprocessor does not raise an exception, assume success.
            return True
    except yt_dlp.DownloadError as e:
        print(f"Download error: {e}")
        return False
    except Exception as e:
        print(f"Error during download: {e}")
        return False

def delete_old_files(download_path, keep_days):
    now = time.time()
    cutoff = now - (keep_days * 86400)
    
    for filename in os.listdir(download_path):
        if filename.endswith(".mp3"):
            file_path = os.path.join(download_path, filename)
            if os.path.getmtime(file_path) < cutoff:
                os.remove(file_path)
                print(f"Deleted old file: {filename}")

def main(config_path):
    config = read_config(config_path)
    urls = config['urls']
    common_options = config['ytdlp_options']
    download_path = config['download_path']
    keep_days = config['keep_days']
    
    # Calculate the start date for the daterange
    start_date = datetime.now() - timedelta(days=keep_days)
    start_date_str = start_date.strftime('%Y%m%d')
    
    # Ensure the download directory exists
    if not os.path.exists(download_path):
        os.makedirs(download_path)
    
    # Change the current working directory to the download path
    os.chdir(download_path)
    
    # Update ytdlp_options to include the daterange
    common_options['daterange'] = yt_dlp.utils.DateRange(start_date_str, '99991231')
    
    last_working_proxy = None
    
    for url in urls:
        attempts = 0
        proxy_attempts = 0
        success = False
        while not success and proxy_attempts < 30:
            if not last_working_proxy or not is_proxy_working(last_working_proxy):
                proxy = get_free_proxy()
                if not proxy or not is_proxy_working(proxy):
                    proxy_attempts += 1
                    continue
                last_working_proxy = proxy
            else:
                proxy = last_working_proxy
            
            while attempts < 2 and not success:
                success = download_video(url, proxy, common_options)
                if not success:
                    print(f"Failed to download {url} with proxy {proxy} on attempt {attempts + 1}. Retrying...")
                    attempts += 1
                    last_working_proxy = None
            
            if not success:
                print(f"Switching proxy after 2 failed attempts with current proxy.")
                proxy_attempts += 1
                attempts = 0
        
        if not success:
            print(f"Failed to download {url} after trying 3 proxies. Giving up.")
    
    delete_old_files(download_path, keep_days)

if __name__ == "__main__":
    main('youtube_proxy.json')