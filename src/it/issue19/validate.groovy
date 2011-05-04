try {

  targetLg = new File(basedir, "target/classes/webapp.properties").size()
  assert targetLg > 0 : "webapp.properties empty (check that compression didn't run over it (=> remove content))"
     
  return true  

} catch(Throwable e) {
  e.printStackTrace()
  return false
}