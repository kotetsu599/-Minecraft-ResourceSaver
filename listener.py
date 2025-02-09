from flask import Flask, request
from mcipc.rcon.je import Client
import subprocess

app = Flask(__name__)

@app.route("/start", methods=["GET"])
def start():
    data = request.args
    password = data.get("password")
    if password != "パスワード":
        return "パスワードが違います。"

    subprocess.run(
        ['screen', '-dmS', 'creative', 'bash', '-c', './start.sh'],
        check=True
    )
    return "開始しています。"

@app.route("/shutdown", methods=["GET"])
def shutdown():
    data = request.args
    password = data.get("password")
    if password != "パスワード":
        return "パスワードが違います。"

    with Client('127.0.0.1', 25575, passwd='rconパスワード') as client:
        client.run('stop')

    subprocess.run(
        ['screen', '-S', 'creative', '-X', 'quit'],
        check=True
    )
    return "シャットダウンしています。"

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)

