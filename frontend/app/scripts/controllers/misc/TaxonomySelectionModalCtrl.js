(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .controller('TaxonomySelectionModalCtrl', function($uibModalInstance, taxonomies) {
            var self = this;

            this.taxonomies = angular.copy(taxonomies);
            this.selectedTags = [];

            this.formData = {
                selectedTaxonomy: null,
                selectedTags: []
            };

            this.selectTaxonomy = function(taxonomy) {
                self.formData.selectedTaxonomy = taxonomy;
                self.search = '';
            }

            this.selectTag = function(tag) {
                tag.selected = !!!tag.selected;

                if(tag.selected === true) {
                    // Add to selection
                    self.formData.selectedTags.push(tag);
                } else {
                    // Remove from selection
                    self.formData.selectedTags = _.without(self.formData.selectedTags, tag);
                }
            }

            this.clearSelection = function() {
                _.each(self.formData.selectedTags, function(tag) {
                    tag.selected = false;
                });

                self.formData.selectedTags = [];
            }

            this.addSelectedTags = function() {
                if (!self.formData.selectedTaxonomy || self.formData.selectedTags.length === 0) {
                    return;
                }

                $uibModalInstance.close(self.formData.selectedTags);
            };

            this.cancel = function() {
                $uibModalInstance.dismiss();
            };
        });
})();
