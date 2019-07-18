# -*- coding: utf-8 -*-
import sys, os
from recommonmark.parser import CommonMarkParser

project = u'OpenDXL Java SDK'
copyright = u'2019, McAfee LLC'

with open('../VERSION', 'r') as content_file:
    VERSION = content_file.read()

version = VERSION
release = VERSION

# General options
needs_sphinx = '1.0'
master_doc = 'index'
pygments_style = 'tango'
add_function_parentheses = True

extensions = ['sphinx.ext.autodoc', 'javasphinx',
              'sphinxcontrib.plantuml']
#'sphinxcontrib-inlinesyntaxhighlight',
templates_path = ['_templates']
exclude_trees = ['.build']
source_suffix = ['.rst', '.md']
source_encoding = 'utf-8-sig'
source_parsers = {
  '.md': CommonMarkParser
}

# HTML options
html_theme = 'sphinx_rtd_theme'
html_short_title = "my-project"
htmlhelp_basename = 'my-project-doc'
html_use_index = True
html_show_sourcelink = False
html_static_path = ['_static']

# PlantUML options
plantuml = os.getenv('plantuml')

html_static_path = ['_static']
html_context = {
    'css_files': [
        '_static/theme_overrides.css',  # override wide tables in RTD theme
        ],
     }

