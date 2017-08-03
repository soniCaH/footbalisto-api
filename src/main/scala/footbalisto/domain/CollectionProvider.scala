package footbalisto.domain

class CollectionProvider() {

  type collectionName = String

  implicit def collectionProvider(model: Model): collectionName = model.collection


}
