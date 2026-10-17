"""Copy a disposable Minecraft test world into the isolated industrial screenshot profile.

The source is read only. Existing prepared worlds are reused, never overwritten.
Does not launch Minecraft, touch installed mods, or copy filesystem links.
"""
import argparse
from pathlib import Path
import shutil


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True,help='Existing disposable test world containing level.dat')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1]
    source=args.source.resolve(strict=True)
    target=(root/'run-industrial-visual/saves/cue-industrial-visual').resolve()
    if not target.is_relative_to(root) or target.is_relative_to(source):
        parser.error('Target must stay inside this checkout and outside the source world')
    if not (source/'level.dat').is_file():parser.error('Source is not a Minecraft test world')
    if target.exists():
        if not (target/'level.dat').is_file():parser.error('Occupied target is not a prepared test world')
        print('REUSED isolated test world:',target);return
    def ignore(directory,names):
        return [name for name in names if name=='session.lock'
                or (Path(directory)/name).is_symlink()
                or getattr(Path(directory)/name,'is_junction',lambda:False)()]
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copytree(source,target,ignore=ignore)
    print('PREPARED isolated test world:',target)


if __name__=='__main__':main()
