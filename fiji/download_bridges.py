# @File(label="Destination folder", style="directory") dest_dir
# @Boolean(label="JRC2018 Female to JFRC2010" ) jrc18f_to_jfrc2010
# @Boolean(label="JRC2018 Female to JFRC2013" ) jrc18f_to_jfrc2013
# @Boolean(label="JRC2018 Female to FCWB" ) jrc18f_to_fcwb
# @Boolean(label="JRC2018 Female to Tefor" ) jrc18f_to_tefor
# @Boolean(label="JRC2018 Female to FAFB" ) jrc18f_to_fafb
# @Boolean(label="JRC2018 Unisex to JFRC2010" ) jrc18u_to_jfrc2010


import os, sys, time, csv
import subprocess, re, urllib2, tempfile, zipfile
import platform
import shutil

from ij import IJ
from fiji.util.gui import GenericDialogPlus

# Many helper methods taken from github.com/jefferis/fiji-cmtk-gui

def template_download_urls():
	return { 
		'jrc18f_to_jfrc2010':'https://ndownloader.figshare.com/files/12919508?private_link=e1edeb2f893d6111e7d4',
		'jrc18f_to_jfrc2013':'https://ndownloader.figshare.com/files/12919832?private_link=a15a5cc56770ec340366',
		'jrc18f_to_fcwb':'https://ndownloader.figshare.com/files/12919868?private_link=6702242e17c564229874',
		'jrc18f_to_tefor':'https://ndownloader.figshare.com/files/12920027?private_link=331564d65f2614af715c',
		'jrc18f_to_fafb':'https://ndownloader.figshare.com/files/12919949?private_link=85b2f2e4f479c94441f2',
		'jrc18u_to_jfrc2010':'https://ndownloader.figshare.com/files/12918830?private_link=b7093bc09a1ea0b5575e' }

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
	

print dest_dir
dest_dir_str = dest_dir.getPath()
print dest_dir_str


if jrc18f_to_jfrc2010:
	print('JRC2018 Female to JFRC2010')
	download_from_url( template_download_urls()['jrc18f_to_jfrc2010'], dest_dir_str )
	
if jrc18f_to_jfrc2013:
	print('JRC2018 Female to JFRC2013')
	download_from_url( template_download_urls()['jrc18f_to_jfrc2013'], dest_dir_str )

if jrc18f_to_fcwb:
	print('JRC2018 Female to FCWB')
	download_from_url( template_download_urls()['jrc18f_to_fcwb'], dest_dir_str )

if jrc18f_to_tefor:
	print('JRC2018 Female to Tefor')
	download_from_url( template_download_urls()['jrc18f_to_tefor'], dest_dir_str )

if jrc18f_to_fafb:
	print('JRC2018 Female to FAFB')
	download_from_url( template_download_urls()['jrc18f_to_fafb'], dest_dir_str )

if jrc18u_to_jfrc2010:
	print('JRC2018 Unisex to JFRC2010')
	download_from_url( template_download_urls()['jrc18u_to_jfrc2010'], dest_dir_str )
	