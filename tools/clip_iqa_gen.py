import numpy as np, onnxruntime as ort, struct
from tokenizers import Tokenizer
from PIL import Image, ImageFilter, ImageDraw

tok = Tokenizer.from_file("tokenizer.json")
txt = ort.InferenceSession("text_int8.onnx", providers=["CPUExecutionProvider"])
vis = ort.InferenceSession("vision_int8.onnx", providers=["CPUExecutionProvider"])

BOS, EOS, CTX = 49406, 49407, 77
def encode(s):
    ids = tok.encode(s).ids
    if not ids or ids[0] != BOS: ids = [BOS] + ids
    if ids[-1] != EOS: ids = ids + [EOS]
    ids = ids[:CTX] + [0]*(CTX-len(ids))
    return ids[:CTX]

def temb(s):
    ids = np.array([encode(s)], dtype=np.int64)
    e = txt.run(None, {"input_ids": ids})[0][0]
    return e/np.linalg.norm(e)

MEAN=np.array([0.48145466,0.4578275,0.40821073],np.float32); STD=np.array([0.26862954,0.26130258,0.27577711],np.float32)
def iemb(img):
    a=np.asarray(img.convert("RGB").resize((224,224)),np.float32)/255.0
    a=(a-MEAN)/STD
    a=np.transpose(a,(2,0,1))[None]
    e=vis.run(None,{"pixel_values":a.astype(np.float32)})[0][0]
    return e/np.linalg.norm(e)

# (group, positive, negative)
PAIRS=[
 ("quality","Good photo.","Bad photo."),
 ("quality","Sharp photo.","Blurry photo."),
 ("quality","Bright photo.","Dark photo."),
 ("quality","High quality photo.","Low quality photo."),
 ("aesthetic","Beautiful photo.","Ugly photo."),
 ("aesthetic","Aesthetic photo.","Not aesthetic photo."),
 ("aesthetic","Professional photo.","Amateur photo."),
 ("interest","An interesting moment.","A boring moment."),
 ("interest","An exciting action shot.","A dull static shot."),
 ("interest","A scenic cinematic shot.","A plain ordinary shot."),
]
vecs=[]; 
for g,p,n in PAIRS: vecs.append(temb(p)); vecs.append(temb(n))
vecs=np.array(vecs,np.float32)
print("generated",vecs.shape,"prompt vectors")

def score(img):
    e=iemb(img); out={}
    for gi,(g,p,n) in enumerate(PAIRS):
        lp=e@vecs[2*gi]*100; ln=e@vecs[2*gi+1]*100
        prob=np.exp(lp)/(np.exp(lp)+np.exp(ln))
        out.setdefault(g,[]).append(prob)
    return {k:float(np.mean(v)) for k,v in out.items()}

# build a structured test frame
img=Image.new("RGB",(256,256),(40,80,140)); d=ImageDraw.Draw(img)
for i in range(0,256,16): d.line([(i,0),(i,256)],fill=(220,200,90),width=2)
d.ellipse([60,60,200,200],fill=(230,120,60)); d.rectangle([90,90,170,170],outline=(255,255,255),width=4)
sharp=img; blurry=img.filter(ImageFilter.GaussianBlur(6))
dark=Image.eval(img,lambda x:int(x*0.22))
print("VERIFY — scores per group (0..1, higher=better):")
for name,im in [("sharp",sharp),("blurry",blurry),("dark",dark)]:
    print(f"  {name:7s}", {k:round(v,3) for k,v in score(im).items()})

# save asset: int32 nVectors, int32 dim, then float32 vectors (pos,neg per pair, in PAIRS order)
import os
out="/Users/burrito/git/ch0p/app/src/main/assets/clip_iqa_prompts.bin"
os.makedirs(os.path.dirname(out),exist_ok=True)
with open(out,"wb") as f:
    f.write(struct.pack("<ii",vecs.shape[0],vecs.shape[1]))
    f.write(vecs.tobytes())
print("wrote",out,os.path.getsize(out),"bytes; PAIRS order:",[g for g,_,_ in PAIRS])
