MVNW := ../dbeaver-common/mvnw

.PHONY: build
build:
	$(MVNW) -T 1C clean verify

jenkins-list-git-branches-plugin.tar.zst: build
	tar -C target -cf - \
		list-git-branches-parameter/ \
		list-git-branches-parameter.hpi \
		list-git-branches-parameter.jar \
		| zstd -T0 -f -o $@
