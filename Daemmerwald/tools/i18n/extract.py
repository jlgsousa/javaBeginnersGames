import re,glob,json
SKIP={'MusicSynth.java','MusicExport.java','Mixamo.java','G3.java','Voice.java','GlbFix.java','Shot.java','Input.java','SkyFogFilter.java','GradeFilter.java'}
lit=re.compile(r'"((?:[^"\\]|\\.)*)"')
def natural(t):
    if not re.search(r'[A-Za-zÄÖÜäöüß]{2}',t): return False
    if re.fullmatch(r'[a-z_0-9:./\-]+',t): return False   # keys, paths
    if re.search(r'\.(png|jpg|glb|ogg|j3md|frag|vert|txt|properties)$',t): return False
    if re.fullmatch(r'[A-Za-z]+[0-9_]+[A-Za-z0-9_]*',t): return False
    if t.startswith('Common/') or t.startswith('Effects/') or t.startswith('Textures/'): return False
    if re.fullmatch(r'(SansSerif|Serif|Monospaced|Dialog)',t): return False
    if re.fullmatch(r'[A-Z][a-zA-Z]+',t) and t in {'Idle','Walk','Run','Survey','Diffuse','Ambient','GlowColor','Color','BaseColor','Specular','Shininess','DiffuseMap','ColorMap','NormalMap','Texture','GlowMap','UseMaterialColors'}: return False
    return True
exact=set(); tmpl=set()
for f in sorted(glob.glob('/home/user/javaBeginnersGames/Daemmerwald/src/main/java/daemmerwald/*.java')):
    if f.split('/')[-1] in SKIP: continue
    for line in open(f):
        st=line.strip()
        if st.startswith('//') or st.startswith('*') or st.startswith('/*') or 'System.out' in line or 'System.err' in line or 'getProperty' in line or 'setProperty' in line or 'flags.' in line and 'say' not in line and 'floatText' not in line: 
            # still allow lines mixing flags with text? keep simple
            if 'flags.' in line and ('"*' in line or 'Text(' in line): pass
            else: continue
        ms=list(lit.finditer(line))
        if not ms: continue
        # group chains
        groups=[]; cur=[ms[0]]
        for a,b in zip(ms,ms[1:]):
            mid=line[a.end():b.start()].strip()
            if mid.startswith('+') and mid.endswith('+') and ',' not in mid.replace('(',' (') or mid=='+':
                # avoid splitting across arguments: if a top-level comma inside mid, break
                depth=0; comma=False
                for ch in mid:
                    if ch in '([': depth+=1
                    elif ch in ')]': depth-=1
                    elif ch==',' and depth<=0: comma=True
                if not comma: cur.append(b); continue
            groups.append(cur); cur=[b]
        groups.append(cur)
        for g in groups:
            parts=[m.group(1) for m in g]
            if not any(natural(p) for p in parts): continue
            for p in parts:
                if natural(p) and len(p)>1: exact.add(p)
            if len(g)>1 or True:
                pre=line[:g[0].start()].rstrip(); post=line[g[-1].end():].lstrip()
                t=''
                if pre.endswith('+'): t+='{}'
                for i,m in enumerate(g):
                    if i>0:
                        mid=line[g[i-1].end():m.start()].strip()
                        if mid!='+': t+='{}'
                    t+=m.group(1)
                if post.startswith('+'): t+='{}'
                if '{}' in t: tmpl.add(t)
json.dump({'exact':sorted(exact),'tmpl':sorted(tmpl)},open('/tmp/claude-0/i18n/cat.json','w'),ensure_ascii=False,indent=0)
print(len(exact),sum(map(len,exact)),len(tmpl),sum(map(len,tmpl)))
