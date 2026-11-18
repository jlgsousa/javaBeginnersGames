import re, hashlib, subprocess, os, sys, wave
from piper import PiperVoice
OUT=os.path.join(os.path.dirname(__file__),'..','..','assets','voice','de')
os.makedirs(OUT+'/f',exist_ok=True); os.makedirs(OUT+'/m',exist_ok=True)
voices={'f':PiperVoice.load(os.environ.get('PIPER_DIR','.')+'/de_DE-kerstin-medium.onnx'),'m':PiperVoice.load(os.environ.get('PIPER_DIR','.')+'/de_DE-thorsten-high.onnx')}
def unesc(s):
    return s.replace('\\"','"').replace('\\\\','\\')
lines=[l for l in open(os.path.join(os.path.dirname(__file__),'spoken.txt')).read().split('\n') if l]
done=0
for raw in lines:
    key=unesc(raw[1:] if raw.startswith('>') else raw)
    h=hashlib.sha1(key.encode('utf-8')).hexdigest()[:16]
    text=key
    m=re.match(r'^([A-ZÄÖÜ][\wäöüß ]{1,25}): (.*)$',text)
    if m: text=m.group(2)
    text=re.sub(r',?\s*\{name\}','',text)
    text=text.replace('„','').replace('“','').replace('»','').replace('«','').replace('✦','').replace('…','...').replace('–',',')
    for g,v in voices.items():
        ogg=f'{OUT}/{g}/{h}.ogg'
        if os.path.exists(ogg): continue
        wav=f'/tmp/voice_{g}.wav'
        with wave.open(wav,'wb') as wf:
            v.synthesize_wav(text, wf)
        subprocess.run(['ffmpeg','-loglevel','error','-y','-i',wav,'-ac','1','-ar','22050','-c:a','libvorbis','-q:a','0',ogg],check=True)
    done+=1
    if done%25==0: print(done, flush=True)
print('fertig', done)
