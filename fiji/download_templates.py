# @File(label="Destination folder", style="directory") dest_dir
# @Boolean(label="JRC 2018 Female Brain" ) do_female_cb
# @Boolean(label="JRC 2018 Male Brain" ) do_male_cb
# @Boolean(label="JRC 2018 Unisex Brain" ) do_unisex_cb
# @Boolean(label="JRC 2018 Female VNC" ) do_female_vnc
# @Boolean(label="JRC 2018 Male VNC" ) do_male_vnc
# @Boolean(label="JRC 2018 Unisex VNC" ) do_unisex_vnc

import os, sys, time, csv
import subprocess, re, urllib2, tempfile, zipfile
import platform
import shutil

from ij import IJ
from fiji.util.gui import GenericDialogPlus

# Many helper methods taken from github.com/jefferis/fiji-cmtk-gui

def template_download_urls():
	return { 
		'JRC 2018 Female Brain':'https://ndownloader.figshare.com/files/12809636?private_link=afa673b1dcd163ad8f3f',
		'JRC 2018 Male Brain':'https://ndownloader.figshare.com/files/12809696?private_link=c410cd9f60b3e478892f',
		'JRC 2018 Unisex Brain':'https://ndownloader.figshare.com/files/12809711?private_link=43ea65ba938e64312f32',
		'JRC 2018 Female VNC':'https://ndownloader.figshare.com/files/12824438?private_link=8103fa90a5cded0509c4',
		'JRC 2018 Male VNC':'https://ndownloader.figshare.com/files/12809825?private_link=592150d8768e5c28588c',
		'JRC 2018 Unisex VNC':'https://ndownloader.figshare.com/files/12809834?private_link=a677185a43121977ce61' }

def myExit(err):
	'''Placeholder until I figure out suitable way to break out of jython script'''
	if err==None:
		err=''
	sys.exit(err)

def download_from_url(download_url,target_dir,download_file=None,
	download_msg=None):
	'''
	download file in temporary location
	move to target_dir
	clean up download
	'''
	from ij import IJ
	print( download_url )
	
	# open url and set up using header information
	u = urllib2.urlopen(download_url)
	headers = u.info()
	download_size = int(headers['Content-Length'])
	print( u )
	print( headers )
	
	if download_file == None:
		if headers.has_key('Content-Disposition'):
			download_file = re.sub(".*filename=","",headers['Content-Disposition'])
		else:
			IJ.error("No filename specified for download and none in http header!")
	if download_msg==None:
		download_msg='Downloading: %s' % (download_file)
	tf=tempfile.NamedTemporaryFile(suffix=download_file, delete=False)
	print 'Downloading '+download_url+' to '+ tf.name
	print "Download size should be %d" % (download_size)
	
	dest_file = os.path.join( target_dir, download_file )
	print 'Destination location %s' % (dest_file)
	
	# Now for the download
	block_size=100000	
	if download_size>block_size:
		bytes_read=0
		while bytes_read<download_size:
			IJ.showStatus("%s (%.1f/%.1f Mb)" % 
				(download_msg,(bytes_read/1000000.0),(download_size/1000000.0)))
			IJ.showProgress(bytes_read,download_size)
			tf.file.write(u.read(block_size))
			bytes_read+=block_size
		IJ.showProgress(1.0)
	else: 
		tf.file.write(u.read())
	
	u.close()
	tf.file.close()
	print ('Downloaded file has size %d')%(os.path.getsize(tf.name))
	tf.close()
	
	shutil.move( tf.name, dest_file )
	IJ.showStatus('Cleaning up!')
	
		
def log(msg):
	'''
	logger that chooses simple prints or ImageJ log as appropriate
	'''
	if is_jython:
		from ij import IJ
		if IJ.debugMode:
			print(msg)
	else:
		print(msg)


print dest_dir
dest_dir_str = dest_dir.getPath()
print dest_dir_str


if do_female_cb:
	print('female')
	download_from_url( template_download_urls()['JRC 2018 Female Brain'], dest_dir_str )
	
if do_male_cb:
	print('male')
	download_from_url( template_download_urls()['JRC 2018 Male Brain'], dest_dir_str )
	
if do_unisex_cb:
	print('unisex')
	download_from_url( template_download_urls()['JRC 2018 Unisex Brain'], dest_dir_str )

if do_female_vnc:
	print('female vnc')
	download_from_url( template_download_urls()['JRC 2018 Female VNC'], dest_dir_str )
	
if do_male_vnc:
	print('male vnc')
	download_from_url( template_download_urls()['JRC 2018 Male VNC'], dest_dir_str )
	
if do_unisex_vnc:
	print('unisex vnc')
	download_from_url( template_download_urls()['JRC 2018 Unisex VNC'], dest_dir_str )
