import copy,importlib.util,json,pathlib,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('validator',ROOT/'scripts/validate_catalog.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class CatalogValidation(unittest.TestCase):
 def setUp(self):self.catalog=json.loads((ROOT/'catalog.example.json').read_text())
 def check(self,c):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d)/'catalog.json';p.write_text(json.dumps(c));return v.validate(p)
 def test_bundled_catalog(self):self.assertEqual(self.check(self.catalog),(5,20))
 def test_duplicate_movie_rejected(self):
  self.catalog['movies'].append(copy.deepcopy(self.catalog['movies'][0]))
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_path_traversal_rejected(self):
  self.catalog['movies'][0]['art']='art/../../private.jpg'
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_http_destination_rejected(self):
  self.catalog['movies'][0]['uri']='http://example.com/movie'
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_empty_collection_rejected(self):
  self.catalog['movies']=[m for m in self.catalog['movies'] if m['collection']!='DC']
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_factory_provider_rejected(self):
  self.catalog['movies'][0]['package']='mediatek.factorymenu.ui'
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_unsafe_home_art_rejected(self):
  self.catalog['homeArtwork']='home/../../private.jpg'
  with self.assertRaises(ValueError):self.check(self.catalog)
 def test_unknown_favourite_rejected(self):
  self.catalog['favorites'][0]='invented-film'
  with self.assertRaises(ValueError):self.check(self.catalog)
if __name__=='__main__':unittest.main()
