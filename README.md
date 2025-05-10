# SafeFile

[![](https://jitpack.io/v/LagradOst/SafeFile.svg)](https://jitpack.io/#LagradOst/SafeFile)

A small and simple Android file storage api because scoped storage is fucked af.

This is a fork of UniFile https://github.com/tachiyomiorg/UniFile but with a kotlin wrapper nulling exceptions.

Note that even if a file exists, calling exists() **may** throw an exception if android hates you because of https://stackoverflow.com/questions/71824925/file-created-with-android-mediastore-is-not-visible-anymore-after-application-re so make sure you only access files you have created yourself. There is no obvious what app see what on higher android levels, so good fucking luck debugging it. Also be aware that renaming a file still keeps the original hidden permissions.

The only sane place to keep files is in the filesDir of the app itself, as then you can use the java files api. 
